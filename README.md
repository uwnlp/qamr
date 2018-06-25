# Question-Answer Meaning Representation (QAMR)

This repository contains the code and data for the paper
[Crowdsourcing Question-Answer Meaning Representations](http://aclweb.org/anthology/N18-2089)
published in NAACL 2018.
There is also a longer [ArXiv version](https://arxiv.org/abs/1711.05885).

Question-Answer Meaning Representations are a new paradigm for representing predicate-argument
structure, which makes use of free-form questions and their answers in order to represent a wide
range of semantic phenomena. The semantic expressivity of QAMR compares to (and in some cases
exceeds) that of existing formalisms, while the representations can be annotated by non-experts
(in particular, using crowdsourcing).

We define QAMRs, develop a crowdsourcing pipeline for gathering them at scale on Mechanical Turk,
gather a dataset of about 5,000 annotated sentences, perform a thorough analysis of this data, and
run some intrinsic baselines on the dataset. In addition to this, concurrent work on
[Supervised Open Information Extraction](http://aclweb.org/anthology/N18-1081)
([code](https://github.com/gabrielStanovsky/supervised-oie))
leverages the QAMR dataset to improve the performance of an Open IE system, especially on less
commonly annotated predicates such as nominalizations.

## Contents

 * `data/`: The officially released data.
 * `code/`: Code and documentation for running the QAMR annotation pipeline and performing the data
 analysis presented in the NAACL paper.
