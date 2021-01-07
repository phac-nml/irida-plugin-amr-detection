package ca.corefacility.bioinformatics.irida.plugin.amrdetection;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import ca.corefacility.bioinformatics.irida.exceptions.IridaWorkflowNotFoundException;
import ca.corefacility.bioinformatics.irida.exceptions.PostProcessingException;
import ca.corefacility.bioinformatics.irida.model.sample.MetadataTemplateField;
import ca.corefacility.bioinformatics.irida.model.sample.Sample;
import ca.corefacility.bioinformatics.irida.model.sample.metadata.MetadataEntry;
import ca.corefacility.bioinformatics.irida.model.sample.metadata.PipelineProvidedMetadataEntry;
import ca.corefacility.bioinformatics.irida.model.workflow.IridaWorkflow;
import ca.corefacility.bioinformatics.irida.model.workflow.analysis.AnalysisOutputFile;
import ca.corefacility.bioinformatics.irida.model.workflow.analysis.type.AnalysisType;
import ca.corefacility.bioinformatics.irida.model.workflow.submission.AnalysisSubmission;
import ca.corefacility.bioinformatics.irida.pipeline.results.updater.AnalysisSampleUpdater;
import ca.corefacility.bioinformatics.irida.service.sample.MetadataTemplateService;
import ca.corefacility.bioinformatics.irida.service.sample.SampleService;
import ca.corefacility.bioinformatics.irida.service.workflow.IridaWorkflowsService;

/**
 * {@link AnalysisSampleUpdater} for AMR detection results to be written to
 * metadata of {@link Sample}s.
 */
public class AMRDetectionUpdater implements AnalysisSampleUpdater {
	private static final Logger logger = LoggerFactory.getLogger(AMRDetectionUpdater.class);
	private static final String STARAMR_SUMMARY = "staramr-summary.tsv";
	private static final String RGI_SUMMARY = "rgi-summary.tsv";

	private static final Splitter SPLITTER = Splitter.on('\t');

	private static final String METADATA_RGI_DRUG_CLASS = "rgi/drug-class";
	private static final String METADATA_RGI_GENE = "rgi/gene";

	private static final String METADATA_STARAMR_GENE = "staramr/gene";
	private static final String METADATA_STARAMR_DRUG_CLASS = "staramr/drug-class";

	private MetadataTemplateService metadataTemplateService;
	private SampleService sampleService;
	private IridaWorkflowsService iridaWorkflowsService;

	/**
	 * Builds a new {@link AMRDetectionUpdater} with the following information.
	 * 
	 * @param metadataTemplateService The {@link MetadatTemplateService}.
	 * @param sampleService           The {@link SampleService}.
	 * @param iridaWorkflowsService   The {@link IridaWorkflowsService}.
	 */
	public AMRDetectionUpdater(MetadataTemplateService metadataTemplateService, SampleService sampleService,
			IridaWorkflowsService iridaWorkflowsService) {
		this.metadataTemplateService = metadataTemplateService;
		this.sampleService = sampleService;
		this.iridaWorkflowsService = iridaWorkflowsService;
	}

	/**
	 * Gets the staramr results from the given output file.
	 * 
	 * @param staramrFilePath The staramr output file containing the results.
	 * @return A {@link AMRResult} storing the results from staramr as
	 *         {@link String}s.
	 * @throws IOException             If there was an issue reading the file.
	 * @throws PostProcessingException If there was an issue parsing the file.
	 */
	private AMRResult getStarAMRResults(Path staramrFilePath) throws IOException, PostProcessingException {
		final int GENOTYPE_INDEX = 1;
		final int DRUG_INDEX = 2;

		final int MAX_TOKENS = 3;

		@SuppressWarnings("resource")
		BufferedReader reader = new BufferedReader(new FileReader(staramrFilePath.toFile()));
		String line = reader.readLine();
		List<String> tokens = SPLITTER.splitToList(line);
		if (tokens.size() != MAX_TOKENS) {
			throw new PostProcessingException("Invalid number of columns in staramr results file [" + staramrFilePath
					+ "], expected [" + MAX_TOKENS + "] got [" + tokens.size() + "]");
		}

		line = reader.readLine();
		tokens = SPLITTER.splitToList(line);
		String genotype = tokens.get(GENOTYPE_INDEX);
		String drug = tokens.get(DRUG_INDEX);

		line = reader.readLine();

		if (line == null) {
			return new AMRResult(genotype, drug);
		} else {
			throw new PostProcessingException("Invalid number of results in staramr results file [" + staramrFilePath
					+ "], expected only one line of results but got multiple lines");
		}
	}

	/**
	 * Gets the RGI results from the given output file.
	 * 
	 * @param rgiFilePath The path to the RGI output file.
	 * @return An {@link AMRResult} object containing the results.
	 * @throws IOException             If there was an issue reading the file.
	 * @throws PostProcessingException If there was an issue parsing the file.
	 */
	private AMRResult getRgiResults(Path rgiFilePath) throws IOException, PostProcessingException {
		final int MAX_TOKENS = 23;

		final int BEST_HIT_ARO_INDEX = 8;
		final int DRUG_CLASS_INDEX = 14;

		final String DRUG_CLASS_SPLIT = ";";

		final Joiner joiner = Joiner.on(", ");

		List<String> genotypes = Lists.newArrayList();
		List<String> drugs = Lists.newArrayList();

		@SuppressWarnings("resource")
		BufferedReader reader = new BufferedReader(new FileReader(rgiFilePath.toFile()));

		String line = reader.readLine();

		List<String> tokens = SPLITTER.splitToList(line);
		if (tokens.size() != MAX_TOKENS) {
			throw new PostProcessingException("Invalid number of columns in RGI results file [" + rgiFilePath
					+ "], expected [" + MAX_TOKENS + "] got [" + tokens.size() + "]");
		}

		line = reader.readLine();
		while (line != null) {
			tokens = SPLITTER.splitToList(line);

			if (tokens.size() != MAX_TOKENS) {
				line = reader.readLine();
				continue;
			}

			String genotype = tokens.get(BEST_HIT_ARO_INDEX);
			String drugClass = tokens.get(DRUG_CLASS_INDEX);

			genotypes.add(genotype);
			drugs.add(drugClass);

			line = reader.readLine();
		}

		if (!genotypes.isEmpty()) {
			Collections.sort(genotypes);

			String genotypesString = joiner.join(genotypes);

			Set<String> drugsSet = Sets.newTreeSet();
			drugs.forEach(t -> drugsSet.addAll(Lists.newArrayList(t.split(DRUG_CLASS_SPLIT))));

			String drugsString = joiner.join(drugsSet);

			return new AMRResult(genotypesString, drugsString);
		} else {
			throw new PostProcessingException("No results found in rgi output file [" + rgiFilePath + "]");
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void update(Collection<Sample> samples, AnalysisSubmission analysis) throws PostProcessingException {
		if (samples.size() != 1) {
			throw new PostProcessingException(
					"Expected one sample; got '" + samples.size() + "' for analysis [id=" + analysis.getId() + "]");
		}

		final Sample sample = samples.iterator().next();

		AnalysisOutputFile staramrAof = analysis.getAnalysis().getAnalysisOutputFile(STARAMR_SUMMARY);

		Path staramrFilePath = staramrAof.getFile();

		AnalysisOutputFile rgiAof = analysis.getAnalysis().getAnalysisOutputFile(RGI_SUMMARY);

		Path rgiFilePath = rgiAof.getFile();

		Map<String, MetadataEntry> stringEntries = new HashMap<>();
		try {
			IridaWorkflow iridaWorkflow = iridaWorkflowsService.getIridaWorkflow(analysis.getWorkflowId());
			String workflowVersion = iridaWorkflow.getWorkflowDescription().getVersion();

			AMRResult staramrResult = getStarAMRResults(staramrFilePath);
			AMRResult rgiResult = getRgiResults(rgiFilePath);

			PipelineProvidedMetadataEntry staramrGenotypeEntry = new PipelineProvidedMetadataEntry(
					staramrResult.getGenotype(), "text", analysis);
			PipelineProvidedMetadataEntry staramrDrugEntry = new PipelineProvidedMetadataEntry(
					staramrResult.getDrugClass(), "text", analysis);
			PipelineProvidedMetadataEntry rgiGenotypeEntry = new PipelineProvidedMetadataEntry(rgiResult.getGenotype(),
					"text", analysis);
			PipelineProvidedMetadataEntry rgiDrugEntry = new PipelineProvidedMetadataEntry(rgiResult.getDrugClass(),
					"text", analysis);

			stringEntries.put(appendVersion(METADATA_STARAMR_GENE, workflowVersion), staramrGenotypeEntry);
			stringEntries.put(appendVersion(METADATA_STARAMR_DRUG_CLASS, workflowVersion), staramrDrugEntry);
			stringEntries.put(appendVersion(METADATA_RGI_GENE, workflowVersion), rgiGenotypeEntry);
			stringEntries.put(appendVersion(METADATA_RGI_DRUG_CLASS, workflowVersion), rgiDrugEntry);

			Set<MetadataEntry> metadataSet = metadataTemplateService.convertMetadataStringsToSet(stringEntries);
			
			sampleService.mergeSampleMetadata(sample,metadataSet);
		} catch (IOException e) {
			logger.error("Got IOException", e);
			throw new PostProcessingException("Error parsing amr detection results", e);
		} catch (IridaWorkflowNotFoundException e) {
			logger.error("Got IridaWorkflowNotFoundException", e);
			throw new PostProcessingException("Workflow is not found", e);
		} catch (Exception e) {
			logger.error("Got Exception", e);
			throw e;
		}
	}

	/**
	 * Appends the name and version together for a metadata field name.
	 * 
	 * @param name    The name.
	 * @param version The version.
	 * @return The appended name and version.
	 */
	private String appendVersion(String name, String version) {
		return name + "/" + version;
	}

	/**
	 * Class used to store together data extracted from the RGI/staramr tables.
	 */
	private class AMRResult {
		private String genotype;
		private String drugClass;

		public AMRResult(String genotype, String drugClass) {
			this.genotype = genotype;
			this.drugClass = drugClass;
		}

		public String getGenotype() {
			return genotype;
		}

		public String getDrugClass() {
			return drugClass;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public AnalysisType getAnalysisType() {
		return AMRDetectionPlugin.AMR_DETECTION;
	}
}
