# Graph induction

This folder contains code for inducting graph structures from the QA pairs, as detailed in [Michael et al. (long version)](https://arxiv.org/pdf/1711.05885.pdf).

To extract the graphs, run:

    get_graphs.sh <input-file> <output-folder>

Where ```input-file``` follows the QA annotation filtered format, e.g., [dev.tsv](../../data/filtered/dev.tsv).<br>
The output folder will contain the graphs in html brat visual format. 
For easy browsing of the graphs, an index.html is created with links to all generated structures.<br>
See example of generated structures in the [output example folder](./example)
