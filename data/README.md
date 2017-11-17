# QAMR Dataset

The data is available in three versions:

  * `full`, preserving all of the information gathered during annotation,
  * `filtered`, removing questions judged invalid or not beginning with _wh_-words, and
  * `readable`, a rendering of the `filtered` set for humans to be able to read.

The filtered version is probably what you want to use if you're postprocessing or modeling the data.

All data is presented in a `.tsv` format.
In the `full` and `filtered` versions, each row corresponds to a single QA pair.
Both versions have the same format
(with the full version allowing for more possibilities in fields 7 and 8):

  0. **Sentence ID (string).** This uniquely identifies the sentence the QA was written for.
     A sentence ID begins either with `Wiki1k` (in the train, dev, and test sets) or `PTB`.
     For the `Wiki1k` IDs, their (pre-tokenized with space-separation) sentences are given in
     `wiki-sentences.tsv`. For the `PTB` IDs, you must have the Penn Treebank corpus and align them
     yourself. (The format of the ID string for these is `PTB:<section>/<filename>:<sentence num>`.)
  1. **Set of target words (space-separated nats).** Together with the sentence ID, this corresponds
     to a single HIT, a unit of work that can be assigned to a worker. The target words are
     indicated by their token indices in the sentence.
  2. **Anonymized worker ID (string).** Each HIT was assigned to some number of workers. The number of
     assignments per worker varied from 1 (in the train set) to 3 (in dev and test) to 5 (in the
     PTB). To subsample from the more densely annotated sets, you would identify a HIT by fields 0
     and 1, and then for each HIT sample all of the QA pairs written by your desired number of
     workers by examining this field. (A single worker could not do the same HIT twice.)
  3. **QA index in the assignment (nat).** Within each assignment we index the QA pairs. That way, the
     first four fields of a row may together be treated as a unique identifier for a QA pair.
     This index is also used for redundancy judgments.
     Not every index will necessarily be included, since some QAs may be filtered out.
  4. **Target word corresponding to QA (nat).** Workers were instructed to use one of the target
     words either in the question or in the answer. This field is the index in the sentence of the
     target word the worker intended to use. In a some cases this does not actually
     appear in the question or answer, usually because of inflectional or derivational morphology.
  5. **Question (string).** The question the worker wrote.
  6. **Answer (space-separated nats).** The answer highlighted by the worker who wrote the question.
     Answers were highlighted as sets of tokens from the sentence, and were almost always
     contiguous. They are denoted by a space-separated list of indices in the sentence
  7. **Validator 1 response (`<worker ID>:<judgment>`).** Before a colon is the anonymized ID of the
     validator who gave the validation judgment. After the colon is their judgment.
     In the filtered set, the judgment is always a space-separated list of nats denoting the tokens
     in the sentence that they highlighted as the answer. In the full set, the judgment may also be
     `Invalid` or `Redundant-i` where `i` is the index of the QA pair (field 3) in the same
     assignment (fields 0-2) that the question was judged to be redundant with.
  8. **Validator 2 response.** Same as 7.

The `readable` version is formatted as a list of sentence blocks.
Each sentence block consists of:

  * `#<Sentence ID>`, denoting the start of the block for the indicated sentence.
  * The sentence, rendered in a human-friendly way.
  * Question-answer pairs, where the question takes the first 50 characters
    (the max allowed question length), followed by the answers separated by tabs.
    Each QA pair gets its own line.
  * A blank line.

There is no `readable` version for the PTB set, because the text of the PTB is owned by the LDC.
The anonymized worker IDs are consistent across all versions and splits.
