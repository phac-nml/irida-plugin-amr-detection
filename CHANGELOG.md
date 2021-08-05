# 0.2.1

* Set RGI database to 'amr_detection'. A database with this name must be preloaded using the RGI data manager tool.

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
