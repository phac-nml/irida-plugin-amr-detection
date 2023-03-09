# 0.3.0

* Updates staramr to version 0.9.1.
* Updates Shovill to version 1.1.0+galaxy1.
* Updates RGI to version 5.2.1.
   * Specific RGI databases can be installed in Galaxy and selected through the pipeline.
* Moved selection of PointFinder organism to a dropdown list when starting pipeline ([PR#20](https://github.com/phac-nml/irida-plugin-amr-detection/pull/20))

# 0.2.0

* Updated writing of metadata to be compatible with IRIDA `21.01` update.
   * This means the plugin now requires IRIDA `21.01+`.
* Updated staramr version to `0.7.2`.
   * The plugin will now save the additional fields provided by the newer staramr version in the metadata table.
* Updated RGI version to `5.1.1`.
* Updated Shovill to version `1.1.0`.
* Fixed bug where empty RGI results would lead to metadata not being written (#8)

# 0.1.0

* Initial release of plugin.
