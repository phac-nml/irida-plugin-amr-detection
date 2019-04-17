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

	private Map<String, List<String>> getStarAMRResults(Path staramrFilePath) throws IOException {
		final int SAMPLE_NAME = 0;
		final int GENOTYPE = 1;
		final int DRUG = 2;

		Map<String, List<String>> staramrEntries = new HashMap<>();

		@SuppressWarnings("resource")
		BufferedReader reader = new BufferedReader(new FileReader(staramrFilePath.toFile()));
		String line = reader.readLine();
		// skip first (header) line
		line = reader.readLine();
		while (line != null) {
			List<String> tokens = SPLITTER.splitToList(line);

			String sampleName = tokens.get(SAMPLE_NAME);
			String genotype = tokens.get(GENOTYPE);
			String drug = tokens.get(DRUG);

			staramrEntries.put(sampleName, Lists.newArrayList(genotype, drug));

			line = reader.readLine();
		}

		return staramrEntries;
	}

	private RGIResult getRgiEntries(Path rgiFilePath) throws IOException {
		final int MAX_TOKENS = 23;
		final int BEST_HIT_ARO = 8;
		final int DRUG_CLASS = 14;

		final String DRUG_CLASS_SPLIT = ";";

		final Joiner joiner = Joiner.on(", ");

		List<String> genotypes = Lists.newArrayList();
		List<String> drugs = Lists.newArrayList();

		@SuppressWarnings("resource")
		BufferedReader reader = new BufferedReader(new FileReader(rgiFilePath.toFile()));

		String line = reader.readLine();
		// skip first (header) line
		line = reader.readLine();
		while (line != null) {
			List<String> tokens = SPLITTER.splitToList(line);

			if (tokens.size() != MAX_TOKENS) {
				line = reader.readLine();
				continue;
			}

			String genotype = tokens.get(BEST_HIT_ARO);
			String drugClass = tokens.get(DRUG_CLASS);

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

			return new RGIResult(genotypesString, drugsString);
		} else {
			return null;
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
		final int GENOTYPE = 0;
		final int DRUG = 1;

		final Sample sample = samples.iterator().next();

		AnalysisOutputFile staramrAof = analysis.getAnalysis().getAnalysisOutputFile(STARAMR_SUMMARY);

		Path staramrFilePath = staramrAof.getFile();

		AnalysisOutputFile rgiAof = analysis.getAnalysis().getAnalysisOutputFile(RGI_SUMMARY);

		Path rgiFilePath = rgiAof.getFile();

		Map<String, MetadataEntry> stringEntries = new HashMap<>();
		try {
			Map<String, List<String>> staramrEntries = getStarAMRResults(staramrFilePath);
			RGIResult rgiResult = getRgiEntries(rgiFilePath);

			IridaWorkflow iridaWorkflow = iridaWorkflowsService.getIridaWorkflow(analysis.getWorkflowId());
			String workflowVersion = iridaWorkflow.getWorkflowDescription().getVersion();

			if (staramrEntries.size() == 1) {
				for (String key : staramrEntries.keySet()) {
					final String genotype = staramrEntries.get(key).get(GENOTYPE);
					final String drug = staramrEntries.get(key).get(DRUG);

					PipelineProvidedMetadataEntry genotypeEntry = new PipelineProvidedMetadataEntry(genotype, "text",
							analysis);
					PipelineProvidedMetadataEntry drugEntry = new PipelineProvidedMetadataEntry(drug, "text",
							analysis);
					stringEntries.put(appendVersion(METADATA_STARAMR_GENE,workflowVersion), genotypeEntry);
					stringEntries.put(appendVersion(METADATA_STARAMR_DRUG_CLASS,workflowVersion), drugEntry);
				}
			} else {
				logger.debug(staramrFilePath + " contains no or invalid staramr results.");
			}

			if (rgiResult != null) {
				PipelineProvidedMetadataEntry genotypeEntry = new PipelineProvidedMetadataEntry(rgiResult.getGenotype(),
						"text", analysis);
				PipelineProvidedMetadataEntry drugEntry = new PipelineProvidedMetadataEntry(
						rgiResult.getDrugClass(), "text", analysis);
				stringEntries.put(appendVersion(METADATA_RGI_GENE,workflowVersion), genotypeEntry);
				stringEntries.put(appendVersion(METADATA_RGI_DRUG_CLASS,workflowVersion), drugEntry);
			} else {
				logger.debug(rgiFilePath + " contains no or invalid rgi results.");
			}

			Map<MetadataTemplateField, MetadataEntry> metadataMap = metadataTemplateService
					.getMetadataMap(stringEntries);

			sample.mergeMetadata(metadataMap);
			sampleService.updateFields(sample.getId(), ImmutableMap.of("metadata", sample.getMetadata()));
		} catch (IOException e) {
			throw new PostProcessingException("Error parsing amr detection results", e);
		} catch (IridaWorkflowNotFoundException e) {
			throw new PostProcessingException("Workflow is not found", e);
		} catch (Exception e) {
			logger.error("Exception", e);
			throw e;
		}
	}
	
	private String appendVersion(String name, String version) {
		return name + "/" + version;
	}

	/**
	 * Class used to store together data extracted from the RGI table.
	 */
	private class RGIResult {
		private String genotype;
		private String drugClass;

		public RGIResult(String genotype, String drugClass) {
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
