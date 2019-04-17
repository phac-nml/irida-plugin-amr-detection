package ca.corefacility.bioinformatics.irida.plugin.amrdetection;

import java.awt.Color;
import java.util.Optional;
import java.util.UUID;

import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginException;
import org.pf4j.PluginWrapper;

import ca.corefacility.bioinformatics.irida.model.workflow.analysis.type.AnalysisType;
import ca.corefacility.bioinformatics.irida.pipeline.results.updater.AnalysisSampleUpdater;
import ca.corefacility.bioinformatics.irida.plugins.IridaPlugin;
import ca.corefacility.bioinformatics.irida.plugins.IridaPluginException;
import ca.corefacility.bioinformatics.irida.service.sample.MetadataTemplateService;
import ca.corefacility.bioinformatics.irida.service.sample.SampleService;
import ca.corefacility.bioinformatics.irida.service.workflow.IridaWorkflowsService;

/**
 * A plugin for detecting AMR genes using both
 * <a href="https://github.com/arpcard/rgi">RGI</a> and
 * <a href="https://github.com/phac-nml/staramr">staramr</a>.
 */
public class AMRDetectionPlugin extends Plugin {

	public static AnalysisType AMR_DETECTION = new AnalysisType("AMR_DETECTION");

	public AMRDetectionPlugin(PluginWrapper wrapper) {
		super(wrapper);
	}

	@Override
	public void start() throws PluginException {
	}

	@Extension
	public static class PluginInfo implements IridaPlugin {

		@Override
		public Optional<AnalysisSampleUpdater> getUpdater(MetadataTemplateService metadataTemplateService,
				SampleService sampleService, IridaWorkflowsService iridaWorkflowsService) throws IridaPluginException {
			return Optional.of(new AMRDetectionUpdater(metadataTemplateService, sampleService, iridaWorkflowsService));
		}

		@Override
		public AnalysisType getAnalysisType() {
			return AMR_DETECTION;
		}

		@Override
		public UUID getDefaultWorkflowUUID() {
			return UUID.fromString("b1a0fd27-2948-41c1-aecc-8f541f1d5dbd");
		}

		@Override
		public Optional<Color> getBackgroundColor() {
			return Optional.of(Color.decode("#66c2a4"));
		}
	}
}
