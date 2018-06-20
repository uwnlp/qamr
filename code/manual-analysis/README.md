# Manual Analyses

Here are the manually annotated samples from our dataset.
For the most detailed explanation of our analysis, see the
[ArXiv version](https://arxiv.org/pdf/1711.05885.pdf)
of the paper.

## Qualitative Data Analysis

We sampled 150 QA pairs and annotated them with labels of what kind of predicate-argument structure
they capture from the original sentence as well as some of the variety of in the ways they do so.

We label the following predicate-argument relations:

* VCORE: Verb core argument role
* VMOD: Verb modifier
* NCORE: Noun core argument role
* NMOD: Noun modifier
* APPCO: Appositive (with DP / coreference)
* COPCO: Copula (with DP / coreference)
* COPMOD: Copula (modifier)
* NUM: count of some measure or count noun
* AMT: amount, including unit of measure
* COEXACT: exact coreference.
* COPART: partial coreference (X was one of Y, X such as Y, etc.)
* NAME: Relation expressed as part of something/someone's name
* OTHER: other relation realized in the sentence.
* REL: relation not realized in the sentence.

We add any number of the following suffixes to the label, which correspond to differences in how the
sentence and the QA pair express the relation in question.

* -CO: at least one of the answers is a coreferent mention of the argument directly expressed in the sentence
* -IMP: the relation is ""implicit,"" i.e., can fit into a syntactic slot or modify something, but is not manifest explicitly
* -VAR: there is syntactic variation from the expression to the sentence that is possibly indicative of the relationship's meaning (e.g., passive alternation, nominalization, etc.)
* -SYN: the relationship is directly expressed in the sentence and question, but the predicates they use are synonyms.
* -FOC: the QA pair highlights the focus of a predicate.
* -!: the QA pair expresses a false conclusion about the sentence.

The statistics reported in the NAACL paper are computed by the analysis code over this annotated data.
