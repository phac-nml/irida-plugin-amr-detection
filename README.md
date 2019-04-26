# IRIDA AMR Detection Pipeline

This project contains an [IRIDA][] plugin for a pipeline to perform AMR detection using [RGI][] and [staramr][].

# Installation

## Installing to IRIDA

Please download the provided `amr-detection-[version].jar` from the [releases][] page and copy to your `/etc/irida/plugins` directory.  Now you may start IRIDA and you should see the pipeline appear in your list of pipelines.

*Note:* This plugin requires you to be running IRIDA version >= `19.01`. Please see the [IRIDA][] documentation for more details.

## Installing Galaxy Dependencies

In order to use this pipeline, you will also have to install the [RGI][], [staramr][], and [shovill][] (for assembly) Galaxy tools within your Galaxy instance. These can be found at:

| Name    | Version  | Galaxy Tool                                                    |
|---------|----------|----------------------------------------------------------------|
| RGI     | `4.2.2` | <https://toolshed.g2.bx.psu.edu/view/card/rgi/715bc9aeef69>    |
| staramr | `0.4.0` | <https://toolshed.g2.bx.psu.edu/view/nml/staramr/930893c83a1a> |
| shovill | `1.0.4` | <https://toolshed.g2.bx.psu.edu/view/iuc/shovill/865119fcb694> |

# Screenshots

## Pipeline

![amr-pipeline.png][]

## Analysis Results

![amr-pipeline-results.png][]

## Metadata Table

![amr-pipeline-metadata.png][]

# Building

## Installing IRIDA Libraries

To build this plugin yourself, you must first install [IRIDA][] to your local Maven repository. Please make sure you are installing the IRIDA version defined in the `irida.version.compiletime` property in the [pom.xml][] file (e.g., `19.01.3`). Or, alternatively, please update the IRIDA dependency version in the `pom.xml` file.

To install the IRIDA libraries to a local Maven repository, please run the following from within the [IRIDA][] project (the `irida/` directory):

```bash
mvn clean install -DskipTests
```

## Building AMR Detection Plugin

Once IRIDA is installed, you may build the pipeline plugin by running the following in this project's directory (the `irida-plugin-amr-detection/` directory):

```bash
mvn clean package
```

This should produce a `target/*.jar` file, which can be copied into `/etc/irida/plugins/`.

# Legal

Copyright 2019 Government of Canada

Licensed under the Apache License, Version 2.0 (the "License"); you may not use
this work except in compliance with the License. You may obtain a copy of the
License at:

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed
under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
CONDITIONS OF ANY KIND, either express or implied. See the License for the
specific language governing permissions and limitations under the License.

[IRIDA]: https://github.com/phac-nml/irida
[RGI]: https://github.com/arpcard/rgi
[staramr]: https://github.com/phac-nml/staramr
[shovill]: https://github.com/tseemann/shovill
[amr-pipeline.png]: images/amr-pipeline.png
[amr-pipeline-results.png]: images/amr-pipeline-results.png
[amr-pipeline-metadata.png]: images/amr-pipeline-metadata.png
[releases]: https://github.com/phac-nml/irida-plugin-amr-detection/releases
[pom.xml]: pom.xml
