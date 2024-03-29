package ca.corefacility.bioinformatics.irida.plugin.amrdetection;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
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

	private static final String RGI_DRUG_CLASS = "rgi/drug-class";
	private static final String RGI_GENE = "rgi/gene";
	//
	// The "Number of Contigs" column is a special case since the name can change
	// (e.g., the full name is like "Number of Contigs Greater Than Or Equal To 300
	// bp" where "300 bp" can change).
	private static final String STARAMR_RESULTS_CONTIGS_PREFIX = "Number of Contigs Greater Than Or Equal To";

	// Maps IRIDA metadata field name to the column name of the staramr results
	// (e.g., "staramr/quality" => "Quality Module")
	//@formatter:off
	private static final Map<String, String> STARAMR_RESULTS_METADATA_MAP = ImmutableMap.<String, String>builder()
		.put("staramr/quality"           , "Quality Module")
		.put("staramr/gene"              , "Genotype")
		.put("staramr/drug-class"        , "Predicted Phenotype")
		.put("staramr/plasmid"           , "Plasmid")
		.put("staramr/mlst-scheme"       , "Scheme")
		.put("staramr/mlst-sequence-type", "Sequence Type")
		.put("staramr/genome-length"     , "Genome Length")
		.put("staramr/n50"               , "N50 value")
		.put("staramr/quality-feedback"  , "Quality Module Feedback")
		.put("staramr/number-contigs"    , STARAMR_RESULTS_CONTIGS_PREFIX)
		.build();
	//@formatter:on
	
	// Maps IRIDA metadata field name to the column name of the RGI results
	// (e.g., "rgi/gene" => "Best_Hit_ARO")
	//@formatter:off
	private static final Map<String, String> RGI_RESULTS_METADATA_MAP = ImmutableMap.<String, String>builder()
		.put(RGI_GENE      , "Best_Hit_ARO")
		.put(RGI_DRUG_CLASS, "Drug Class")
		.build();
	//@formatter:on

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
	 * Parses a line of the results file and gets a Map linking the column to the
	 * value in the line. (e.g., "N50 value" => "100").
	 * 
	 * @param columnNames        A List of names of the columns in the results file.
	 * @param line               The line to parse.
	 * @param singleColumnPrefix A prefix for a special case in the staramr results
	 *                           where the column prefix is constant but the suffix
	 *                           changes. Set to null to ignore.
	 * @param resultsFile        The specific file being parsed (for error
	 *                           messages).
	 * @param analysis           The analysis submission being parsed (for error
	 *                           messages).
	 * @return A Map linking the column to the value for the line.
	 * @throws PostProcessingException If there was an error parsing the results.
	 */
	private Map<String, String> getDataMapForLine(List<String> columnNames, String line, String singleColumnPrefix,
			Path resultsFile, AnalysisSubmission analysis) throws PostProcessingException {
		Map<String, String> dataMap = new HashMap<>();

		List<String> values = SPLITTER.splitToList(line);

		if (columnNames.size() != values.size()) {
			throw new PostProcessingException("Mismatch in number of column names [" + columnNames.size()
					+ "] and number of files [" + values.size() + "] in results file [" + resultsFile + "]");
		}

		for (int i = 0; i < columnNames.size(); i++) {
			String column = columnNames.get(i);
			String value = values.get(i);

			if (singleColumnPrefix != null && column.startsWith(singleColumnPrefix)) {
				dataMap.put(singleColumnPrefix, value);
			} else {
				dataMap.put(column, value);
			}
		}

		return dataMap;
	}

	/**
	 * Gets the staramr results from the given output file.
	 * 
	 * @param staramrFilePath The staramr output file containing the results.
	 * @param analysis        The {@link AnalysisSubmission} containing the results.
	 * @return A {@link Map} storing the results from staramr, keyed by the metadata
	 *         field name.
	 * @throws IOException             If there was an issue reading the file.
	 * @throws PostProcessingException If there was an issue parsing the file.
	 */
	private Map<String, PipelineProvidedMetadataEntry> getStarAMRResults(Path staramrFilePath,
			AnalysisSubmission analysis) throws IOException, PostProcessingException {
		final int MIN_TOKENS = 2;

		Map<String, PipelineProvidedMetadataEntry> results = new HashMap<>();
		Map<String, String> dataMap;

		@SuppressWarnings("resource")
		BufferedReader reader = new BufferedReader(new FileReader(staramrFilePath.toFile()));
		String line = reader.readLine();
		List<String> columnNames = SPLITTER.splitToList(line);
		if (columnNames.size() < MIN_TOKENS) {
			throw new PostProcessingException("Invalid number of columns in staramr results file [" + staramrFilePath
					+ "], expected at least [" + MIN_TOKENS + "] got [" + columnNames.size() + "]");
		}

		line = reader.readLine();

		if (line == null || line.length() == 0) {
			dataMap = new HashMap<>();
			logger.info(
					"Got empty results for staramr file [" + staramrFilePath + "] for analysis submission " + analysis);
		} else {
			dataMap = getDataMapForLine(columnNames, line, STARAMR_RESULTS_CONTIGS_PREFIX, staramrFilePath, analysis);
		}

		for (String resultsFieldName : STARAMR_RESULTS_METADATA_MAP.keySet()) {
			String staramrColumnName = STARAMR_RESULTS_METADATA_MAP.get(resultsFieldName);
			String value = dataMap.containsKey(staramrColumnName) ? dataMap.get(staramrColumnName) : "-";

			results.put(resultsFieldName, new PipelineProvidedMetadataEntry(value, "text", analysis));
		}

		line = reader.readLine();

		if (line == null) {
			return results;
		} else {
			throw new PostProcessingException("Invalid number of results in staramr results file [" + staramrFilePath
					+ "], expected only one line of results but got multiple lines");
		}
	}

	/**
	 * Gets the RGI results from the given output file.
	 * 
	 * @param rgiFilePath The path to the RGI output file.
	 * @param analysis    The {@link AnalysisSubmission} containing the results.
	 * @return A {@link Map} object containing the results, keyed by the metadata
	 *         field name.
	 * @throws IOException             If there was an issue reading the file.
	 * @throws PostProcessingException If there was an issue parsing the file.
	 */
	private Map<String, PipelineProvidedMetadataEntry> getRgiResults(Path rgiFilePath, AnalysisSubmission analysis)
			throws IOException, PostProcessingException {
		final String DRUG_CLASS_SPLIT = ";";

		final Joiner joiner = Joiner.on(", ");

		List<String> genotypes = Lists.newArrayList();
		List<String> drugs = Lists.newArrayList();

		@SuppressWarnings("resource")
		BufferedReader reader = new BufferedReader(new FileReader(rgiFilePath.toFile()));

		String line = reader.readLine();

		List<String> columnNames = SPLITTER.splitToList(line);
		if (columnNames.isEmpty()) {
			logger.warn("Missing columns in RGI results file [" + rgiFilePath + "] for analysis submission " + analysis);
		}

		line = reader.readLine();
		while (line != null) {
			Map<String, String> lineDataMap = getDataMapForLine(columnNames, line, null, rgiFilePath, analysis);
			
			if (lineDataMap.containsKey(RGI_RESULTS_METADATA_MAP.get(RGI_GENE))) {
				genotypes.add(lineDataMap.get(RGI_RESULTS_METADATA_MAP.get(RGI_GENE)));
			}
			
			if (lineDataMap.containsKey(RGI_RESULTS_METADATA_MAP.get(RGI_DRUG_CLASS))) {
				drugs.add(lineDataMap.get(RGI_RESULTS_METADATA_MAP.get(RGI_DRUG_CLASS)));
			}

			line = reader.readLine();
		}

		String genotypesString = "-";
		String drugsString = "-";

		if (genotypes.isEmpty()) {
			logger.info("No genotype results found in rgi output file [" + rgiFilePath + "], for analysis submission "
					+ analysis);
		} else {
			Collections.sort(genotypes);

			genotypesString = joiner.join(genotypes);
		}

		if (drugs.isEmpty()) {
			logger.info("No drug results found in rgi output file [" + rgiFilePath + "], for analysis submission "
					+ analysis);
		} else {
			Set<String> drugsSet = Sets.newTreeSet();
			drugs.forEach(t -> drugsSet.addAll(Lists.newArrayList(t.split(DRUG_CLASS_SPLIT))));

			drugsString = joiner.join(drugsSet);
		}

		Map<String, PipelineProvidedMetadataEntry> results = new HashMap<>();
		results.put(RGI_GENE, new PipelineProvidedMetadataEntry(genotypesString, "text", analysis));
		results.put(RGI_DRUG_CLASS, new PipelineProvidedMetadataEntry(drugsString, "text", analysis));

		return results;
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

			Map<String, PipelineProvidedMetadataEntry> staramrResult = getStarAMRResults(staramrFilePath, analysis);
			Map<String, PipelineProvidedMetadataEntry> rgiResult = getRgiResults(rgiFilePath, analysis);

			List<Map<String, PipelineProvidedMetadataEntry>> resultsList = Arrays.asList(staramrResult, rgiResult);

			for (Map<String, PipelineProvidedMetadataEntry> result : resultsList) {
				for (String fieldName : result.keySet()) {
					stringEntries.put(appendVersion(fieldName, workflowVersion), result.get(fieldName));
				}
			}

			Set<MetadataEntry> metadataSet = metadataTemplateService.convertMetadataStringsToSet(stringEntries);

			sampleService.mergeSampleMetadata(sample, metadataSet);
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
	 * {@inheritDoc}
	 */
	@Override
	public AnalysisType getAnalysisType() {
		return AMRDetectionPlugin.AMR_DETECTION;
	}
}
