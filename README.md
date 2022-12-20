[![GitHub release](https://img.shields.io/github/release/phac-nml/irida-plugin-amr-detection.svg)](https://github.com/phac-nml/irida-plugin-amr-detection/releases/latest)
[![Gitter](https://img.shields.io/gitter/room/phac-nml/irida.svg)](https://gitter.im/irida-project/Lobby)

# IRIDA AMR Detection Pipeline

This project contains an [IRIDA][] plugin for a pipeline to perform AMR detection using [RGI][] and [staramr][].

* [Installation](#installation)
    * [Installing to IRIDA](#installing-to-irida)
    * [Installing Galaxy Dependencies](#installing-galaxy-dependencies)
* [Tutorial](#tutorial)
    * [Tutorial Data](#tutorial-data)
    * [Adding Samples to the Cart](#adding-samples-to-the-cart)
    * [Selecting a Pipeline](#selecting-a-pipeline)
    * [Selecting Parameters](#selecting-parameters)
    * [Monitoring Pipeline Status](#monitoring-pipeline-status)
    * [Analysis Results](#analysis-results)
    * [Metadata Table](#metadata-table)
* [Building](#building)
    * [Installing IRIDA Libraries](#installing-irida-libraries)
    * [Building AMR Detection Plugin](#building-amr-detection-plugin)
* [Legal](#legal)
 
# Installation

## Installing to IRIDA

Please download the provided `amr-detection-[version].jar` from the [releases][] page and copy to your `/etc/irida/plugins` directory.  Now you may start IRIDA and you should see the pipeline appear in your list of pipelines.

*Note:* This plugin requires you to be running IRIDA version >= `21.01`. Please see the [IRIDA][] documentation for more details.

## Installing Galaxy Dependencies

In order to use this pipeline, you will also have to install the [RGI][], [staramr][], and [shovill][] (for assembly) Galaxy tools within your Galaxy instance. These can be found at:

| Name    | Version          | Galaxy Tool                                                    |
|---------|------------------|----------------------------------------------------------------|
| RGI     | `5.2.1`          | <https://toolshed.g2.bx.psu.edu/view/card/rgi/84bd24ac33fd>    |
| staramr | `0.9.1+galaxy0`  | <https://toolshed.g2.bx.psu.edu/view/nml/staramr/4d83eccf5f81> |
| shovill | `1.1.0+galaxy1`  | <https://toolshed.g2.bx.psu.edu/view/iuc/shovill/ad80238462c1> |

If you run into issues when installing RGI into Galaxy related to dependency resolution issues in Conda, then the following may help install RGI 5.2.1 into Galaxy:

Using the Conda installation that Galaxy uses (which is likely different from the system's Conda installation), run the following prior to installing the RGI tool in Galaxy:

```
conda install -c conda-forge -c bioconda -c defaults mamba -y
mamba create -c iuc -c conda-forge -c bioconda -c defaults --no-channel-priority --name __rgi@5.2.1 rgi=5.2.1 -y
```

This code will install mamba into the Conda environment used by Galaxy, which is better at performing dependency resolution, and then it will create an RGI Conda environment named `__rgi@5.2.1` with RGI v5.2.1 installed within it. This environment will be named in a way that Galaxy expects, which is important for the next step.

Attempt to install RGI v5.2.1 in Galaxy as normal. Behind the scences, Galaxy will recognize that the previously created RGI conda environment exists and it will use that existing installation.

Finally, the RGI database will need to be installed manually. On the Galaxy admin page, navigate to the "Local Data" section. You should find the "RGI Database Builder" button below the "Installed Data Managers" section. Click the "RGI Database Builder" button and you will be brought to a new page where you can start a job to download and build the database. You should update the "Database name" section (ex: "CARD") before starting the job. Once you hit the execute button, Galaxy will start to download and build the database.

![rgi-database-builder][]

# Tutorial

This tutorial shows how to run the **AMR Detection** pipeline in IRIDA.

## Tutorial Data

The pipeline requires as input paired-end sequence reads in FASTQ format. We will be using Illumina MiSeq data from a sample of **Campylobacter jejuni** for this tutorial with run ID `SRR8914694`. Paired-end fastq files can be downloaded from <https://www.ebi.ac.uk/ena/data/view/SRR8914694> (the **FASTQ files (FTP)** links). A collection of additional example data can be found in the [IRIDA Sample Data][] package.

## Adding Samples to the Cart

Once samples are created for the data and the files are uploaded, you can select the samples you wish to run and add them to the cart by clicking the **Add to Cart** button.

![add-to-cart.png][]

Once you have selected your samples, you can click on the **Cart** button to move to selecting a pipeline.

![cart-button.png][]

## Selecting a Pipeline

Once inside the cart, you should see a card for the **AMR Detection** pipeline.

![amr-pipeline.png][]

Please click the **Select** button to proceed with the pipeline.

## Selecting Parameters

Once the pipeline is selected, the next page provides an overview of all the input files, as well as the option to modify parameters.

![amr-detection-pipeline-page.png][]

Please make sure the **Save AMR detection results to Project Line List Metadata** is selected so that results get saved to the Line List. Also, please click the **Customize** button so that we can modify the PointFinder parameters.

![modify-parameters.png][]

In particular, you may want to modify the **Scan for point mutations using the selected PointFinder database** parameter, changing the value to `campylobacter`. If you which to completely disable searching the PointFinder database, you can instead set this to `disabled` (the default value).

If you know the organism you may also want to set the lower/upper bounds for the genome length. This only impacts the output of the quality module (which checks if the assembled genome is within these bounds). In this case the organism is *Campylobacter jejuni* so let's set the lower bound to `1400000` and upper bound to `1900000`.

![parameters-lower-bound.png][]

![parameters-upper-bound.png][]

Once all your parameters are set, you can click the **Ready to Launch?** button to launch the pipeline.

![launch.png][]

## Monitoring Pipeline Status

To monitor the status of the launched pipeline, please select the **Analyses > Your Analyses** menu.

![your-analyses.png][]

From here, you can monitor the status of your pipeline.

![monitor-analyses.png][]

## Analysis Results

Once the analysis pipeline is finished, you can view the analysis results in your browser or download the files to your machine.

![amr-pipeline-results.png][]

These results show you both the [staramr][] and [RGI][] AMR results on the data, assembled with [shovill][]. The staramr results are all contained within a single Excel file shown here, which lists the detected antimicrobial resistance genes, MLST sequence type, plasmid incompatibility factors among other information. For details on how to interpret these, please see the documentation of the respective projects.

## Metadata Table

If you selected the **Save AMR detection results to Project Line List Metadata** option when launching the pipeline, then both the predicted AMR genes and drug resistances will be integrated into the IRIDA Line List/Metadata table as shown below.

![amr-pipeline-metadata.png][]

# Building

## Installing IRIDA Libraries

To build this plugin yourself, you must first install [IRIDA][] to your local Maven repository. Please make sure you are installing the IRIDA version defined in the `irida.version.compiletime` property in the [pom.xml][] file (e.g., `21.01`). Or, alternatively, please update the IRIDA dependency version in the `pom.xml` file.

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
[parameters-lower-bound.png]: images/parameters-lower-bound.png
[parameters-upper-bound.png]: images/parameters-upper-bound.png
[add-to-cart.png]: images/add-to-cart.png
[cart-button.png]: images/cart-button.png
[amr-detection-pipeline-page.png]: images/amr-detection-pipeline-page.png
[modify-parameters.png]: images/modify-parameters.png
[launch.png]: images/launch.png
[your-analyses.png]: images/your-analyses.png
[monitor-analyses.png]: images/monitor-analyses.png
[releases]: https://github.com/phac-nml/irida-plugin-amr-detection/releases
[pom.xml]: pom.xml
[IRIDA Sample Data]: https://irida.corefacility.ca/downloads/data/irida-sample-data.zip
