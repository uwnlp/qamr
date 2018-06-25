# Graph induction

This folder contains code for inducting graph structures from the QA pairs, as detailed in [Michael et al. (long version)](https://arxiv.org/pdf/1711.05885.pdf).

To extract the graphs, run:

    get_graphs.sh <input-file> <output-folder>

Where ```input-file``` follows the QA annotation filtered format, e.g., [dev.tsv](../../data/filtered/dev.tsv).
The output folder will contain the graphs in html brat visual format. For convince, 
an index.html file will contain links to all generated structures.
See example of generated structures in the [output example folder](./example)
