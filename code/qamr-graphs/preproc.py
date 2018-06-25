# TODO:
# - Is there a way to make the alignment also consider position in phrase?
#   Currently it scores based on the position in the sentence.
#   I don't think that it should happen that two words in the QA will match
#   only a single word in the sentence
# Ideas:
# - Is there a signal in looking at the positions in question and in answer?
# Questions:
# - Account for inflections between answer and sentence?

import pandas as pd
import numpy as np
import nltk
from nltk.corpus import stopwords, wordnet
from nltk.stem.wordnet import WordNetLemmatizer
import logging
logging.basicConfig(level = logging.DEBUG)
from fuzzywuzzy import process
from fuzzywuzzy.utils import full_process
from fuzzywuzzy.string_processing import StringProcessor
from solvers import discrete_brute_minimizer, mean_distance_from_centroid
from operator import itemgetter
from pandas import Series
from copy import copy
import string
from fuzzywuzzy.utils import asciidammit
from itertools import groupby
from operator import itemgetter


import itertools
import operator

def most_common(L):
    """
    Get most common element in a list.
    https://stackoverflow.com/questions/1518522/python-most-common-element-in-a-list
    """
    # get an iterable of (item, iterable) pairs
    SL = sorted((x, i) for i, x in enumerate(L))
    groups = itertools.groupby(SL, key=operator.itemgetter(0))
    # auxiliary function to get "quality" for an item
    def _auxfun(g):
        item, iterable = g
        count = 0
        min_index = len(L)
        for _, where in iterable:
            count += 1
            min_index = min(min_index, where)
        return count, -min_index
    # pick the highest-count/earliest item
    return max(groups, key=_auxfun)[0]

def group_consecutive(data):
    """
    Returns consecutive lists in data
    """
    return [map(itemgetter(1), g)
            for k, g in groupby(enumerate(data), lambda (i,x):i-x)]

def enum(**enums):
    """
    Enum-like emulation for python 2.7
    https://stackoverflow.com/questions/36932/how-can-i-represent-an-enum-in-python
    """
    return type('Enum', (), enums)

def graph_to_triples(digraph, sent):
    """
    Convert a single graph to triples
    """
    return ["{}\t{}\t{}".format(" ".join(sent[u[0]: u[1]]),
                                " ".join(digraph[u][v]["label"]),
                                " ".join(sent[v[0]: v[1]]))
            for (u, v) in digraph.edges()]

def tokenize_experiment(exp_fn, out_fn):
    """
    Tokenizes the sentences and phrases in a given experiment file
    outputs the tokenized version into out_fn
    """
    df = pd.read_csv(exp_fn, header = None)
    for i in df:
        # Iterate over all fields and tokenize strings
        df[i] = [" ".join(nltk.word_tokenize(s if isinstance(s, basestring) else "")) for s in df[i]]

    # Write to output file
    df.to_csv(out_fn, header = False, index = False)

class Aligner:
    """
    Perform QA-SRL alignments between QA pairs and the original sentence
    """

    def __init__(self):
        """
        Initialize class members:
        - lematizer
        """
        self.lmtzr = WordNetLemmatizer()

    def align_experiment(self, exp_fn, out_fn):
        """
        Aligns the QAs with the sentences in a given *tokenized* experiment file
        outputs the aligned version into out_fn
        """
        df = pd.read_csv(exp_fn,
                         names = ["WID", "special", "question", "answer"],
                         header = None
        )
        df["aligned_question"] = Series([""] * len(df["WID"]))
        df["aligned_answer"] = Series([""] * len(df["WID"]))
        cur_sent = None
        for row_index, row in df.iterrows():
            if not(isinstance(row["question"], basestring) and \
                   isinstance(row["answer"], basestring)):
                # This is a row introducing a new sentence
                cur_sent = row["WID"].split(" ")

                # Add POS data to output sentence as the second value
                df.set_value(row_index,
                             "special",
                             " ".join([pos
                                       for (w, pos)
                                       in safe_pos_tag(cur_sent)]))

            else:
                # This is a QA pair relating to a previously introduced sentence
                # Align it and write to df
                cur_question = row["question"].split(" ")
                cur_answer = row["answer"].split(" ")
                question_align, answer_align = self.align_qa(cur_sent,
                                                             cur_question,
                                                             cur_answer,
                                                             lemmatize = True
                )
                df.set_value(row_index, 'aligned_question', format_alignment(cur_question, cur_sent, question_align))
                df.set_value(row_index, 'aligned_answer', format_alignment(cur_answer, cur_sent, answer_align))

        # Write to output file
        df.to_csv(out_fn, header = False, index = False)

    def align_qa(self, sentence, question, answer,
                 lemmatize):
        """
        Grounds a QA against the sentence using fuzzy matching (all tokenized strings in lists)
        Returns QA pair as a list of indices aligning with the sentence (or -1 if not found in the sentence)
        Assumes each word in the sentence can appear only once in the QA.
        lemmatize - whether to perform matching on lemmas instead of surface words
        """
        ## Start with the answer for several reasons:
        ## 1. Should align more easily - all of the words in the answer? (what to do if not?) That's why we include
        ##    stopwords for the answer.
        ## 2. Hopefully there's a true alignemnt available in the annotation
        ## 3. Probably longer than the answer?

        # Lemmatize all elements if indicated
        if lemmatize:
            sentence = self.lemmatize_phrase(sentence)
            question = self.lemmatize_phrase(question)
            answer = self.lemmatize_phrase(answer)

        answer_alignment = self.align_phrase(sentence, answer, align_stopwords = False)

        # Store sentence without the words already aligned with the answer, while recording the original indices
        sent_wo_answer = [(i, w) for i, w in enumerate(self.lemmatize_phrase(sentence)
                                                       if lemmatize else sentence)
                          if i not in answer_alignment]
        map_to_sent = map(itemgetter(0), sent_wo_answer)

        # Align the question to this version of the sentence and alignemnt to indices in the original sentence
        question_alignment = [map_to_sent[i] if (i >= 0) else i
                              for i in
                              self.align_phrase(map(itemgetter(1),
                                                    sent_wo_answer),
                                                self.lemmatize_phrase(question) if lemmatize else question,
                                                align_stopwords = False
                              )]

        # Try to greedily add stopwords
        answer_alignment = self.extend_alignment(sentence, answer, answer_alignment)

        return question_alignment, answer_alignment

    def lemmatize_phrase(self, phrase):
        """ Return a lemmatized version of the tokenized input phrase """
        return [self.lmtzr.lemmatize(w, get_wordnet_pos(pos))
                for (w, pos) in safe_pos_tag(phrase)]

    def align_phrase(self, sentence, phrase, align_stopwords):
        """
        Grounds a phrase against the sentence using fuzzy matching (both tokenized strings)
        Returns a list of indices aligning with the sentence (or a negative if not found in the sentence)
        Assumes each word in the sentence can appear only once in the phrase.
        align_stopwords - contorls whether to align english stopwords (and punctuation)
        """
        # Words to ignore while aligning
        ignore_word = is_extended_stop_word \
                      if (not align_stopwords) and\
                         any([not(is_extended_stop_word(w.lower())) for w in phrase]) \
                      else lambda _: False


        limit = 10
        ret = [-1] * len(phrase)

        # For each word in the phrase, find all words in the sentence which are close enough to it
        possible_indices = [(i, fuzzy_match_word(w, sentence, limit, include_stopwords = align_stopwords))
                            for i, w in enumerate(phrase)
                            if (not ignore_word(w.lower()))]
        non_empty = [(i, x) for i, x in possible_indices if x]

        # Find an assignment which minimizes some density function, only for words with non-empty alignments
        ass = discrete_brute_minimizer([opts + ([-1 * len(sentence)]
                                                if contained_in_others(opts,
                                                                       [ls for (i, ls) in non_empty
                                                                        if i != word_ind])
                                                else []) # Add a "don't map option" in case of duplicates
                                        for word_ind, opts in non_empty],
                                       func = mean_distance_from_centroid)[0]

        # Map back to words in the sentence
        for ind, val in zip(map(itemgetter(0), non_empty),
                            ass):
            ret[ind] = val

        return ret

    def extend_alignment(self, sent, phrase, alignment):
        """
        Given an alignemnt of phrase to a sentence, try extending it verbatim
        by greedily adding neighbouring words in the phrase.
        This is meant to add stopwords in a reasonbly efficient manner
        (adding them to proper align leads to expontential explosion)
        """
        ret = copy(alignment)
        change = True
        while change:
            change = False
            for (phrase_ind, word), sent_ind in zip(enumerate(phrase), ret):
                if sent_ind == -1:
                    try:
                        # This word isn't mapped
                        if (phrase_ind > 0) and (ret[phrase_ind -1] != -1)\
                           and (sent[ret[phrase_ind - 1] + 1] == phrase[phrase_ind]) \
                           and (ret[phrase_ind - 1] + 1 < len(sent)):
                            ret[phrase_ind] = ret[phrase_ind - 1] + 1
                            change = True
                        elif (phrase_ind < len(phrase) -1) and (ret[phrase_ind + 1] != -1)\
                             and (sent[ret[phrase_ind + 1] - 1] == phrase[phrase_ind])\
                             and (ret[phrase_ind + 1] - 1 >= 0):
                            ret[phrase_ind] = ret[phrase_ind + 1] - 1
                            change = True
                    except:
                        pass
        return ret

def contained_in_others(ls, others):
    """
    Identifies whether all of the elements in ls are contained in the lists
    of others (a list of lists of items (O(n^2))
    """
    return all(any([elem in other_ls for other_ls in others]) for elem in ls)

def is_extended_stop_word(word):
    """
    Identify whether a word is a stopword in a slightly extended sense, which includes:
    - punctuation
    - contractions
    """
    return True if \
        word.startswith("'") \
        or (word in stopwords.words('english'))\
        or (all([w in string.punctuation for w in list(word)]))\
        else False

def fuzzy_match_word(word, words, limit,
                     include_stopwords = False):
    """
    Fuzzy find the indexes of word in words, returns a list of indexes which match the
    best return from fuzzy.
    limit controls the number of choices to allow.
    include_stopwords controls whether the word can be matched against a stopword in words.
    """
    from nltk.corpus import stopwords
    ignore_word = is_extended_stop_word if (not include_stopwords) \
                      else lambda _: False

    # Try finding exact matches
    exact_matches = set([i for (i, w) in enumerate(words) if w == word])
    if exact_matches:
        return list(exact_matches)

    # Allow some variance which extractOne misses
    # For example:
    # "Armstrong World Industries Inc" in
    # "Armstrong World Industries Inc. agreed in principle to sell its carpet operations to Shaw Industries Inc ."

    # Start by removing stopwords if flag indicates so
    filter_words = [w for w in words if
                    (not ignore_word(w.lower()))]
    best_matches  = [w for (w, s) in
                     process.extract(word, filter_words, processor = semi_process, limit = limit)
                     if (s > 70)]
    return list(exact_matches.union([i for (i, w) in enumerate(words) if w in best_matches]))


def semi_process(s, force_ascii=False):
    """
    Variation on Fuzzywuzzy's full_process:
    Process string by
    XX removing all but letters and numbers --> These are kept to keep consecutive spans
    -- trim whitespace
    XX force to lower case --> These are kept since annotators marked verbatim spans, so case is a good signal
    if force_ascii == True, force convert to ascii
    """

    if s is None:
        return ""

    if force_ascii:
        s = asciidammit(s)
    # Remove leading and trailing whitespaces.
    string_out = StringProcessor.strip(s)
    return string_out

def format_alignment(phrase, sent, alignemnt):
    """
    Format a given alignment using words from the sentence where possible
    """
    return ' '.join([format_index(sent_ind, sent[sent_ind]) if sent_ind >=0 else phrase[phrase_ind]
                     for phrase_ind, sent_ind in enumerate(alignemnt)])

def format_index(index, word):
    """
    Format index of word in sentence to appear in the aligned version
    """
    return "{{{{{}|{}}}}}".format(index, word)


def get_wordnet_pos(treebank_tag):
    """
    Convert from WSJ to WordNet POS tag
    http://stackoverflow.com/questions/15586721/wordnet-lemmatization-and-pos-tagging-in-python
    """
    if treebank_tag.startswith('J'):
        return wordnet.ADJ
    elif treebank_tag.startswith('V'):
        return wordnet.VERB
    elif treebank_tag.startswith('N'):
        return wordnet.NOUN
    elif treebank_tag.startswith('R'):
        return wordnet.ADV
    else:
        return wordnet.NOUN

def split_list(ls, sep):
    """
    Split list similarly to split string, according to some separator
    """
    ret = []
    cur = []
    for x in ls:
        if (x != sep):
            cur.append(x)
        elif cur:
            ret.append(cur)
            cur = []
    if cur:
        ret.append(cur)
    return ret

def is_subchunk((s1, e1), (s2, e2)):
    """
    Receives two chunks, and returns true iff the
    first chunk is a subchunk of the second
    """
    return (s1 >= s2) and (e1 <= e2)

def uniq(ls):
    """
    uniqify a list
    """
    return list(set(ls))

def reachable_from(graph, node):
    """
    Given an nx graph and a node within this graph,
    return all of the nodes in the same connected component as the input node.
    """
    import networkx as nx
    if isinstance(graph, nx.DiGraph):
        conn = nx.all_pairs_node_connectivity(graph)
        return [n1 for (n1, dist) in conn[node].iteritems() if dist > 0]

    elif isinstance(graph, nx.Graph):
        for comp in nx.connected_components(graph):
            if node in comp:
                return comp


    # Shouldn't get here
    # since the node should appear in some connected component of the graph
    raise Exception("Node {} not in graph".format(node))


def non_projective_edge(e1, e2):
    """
    Returns true iff the given edges (e1, e2) are non-projective
    """
    if len(set(e1 + e2)) != 4:
        # If the edge shares at least a single vertex -
        # it's projective
        return False

    # Get a sorted list of verices
    sort_by_edge = map(itemgetter(0),
                       sorted([(0, x) for x in e1] + [(1, x) for x in e2],
                              key = lambda (e, val): val))

    # If we have an interleaving when sorting - the edges are non-projective
    return (sort_by_edge[0] == sort_by_edge[2])

def intersecting_spans(sp1, sp2):
    """
    Return true iff the two given spans "cross" each other
    meaning that one doesn't contain other yet their intersection isn't empty
    """
    # This is actually the same as non-projectiveness
    # but code would look weird if we used the same name
    return non_projective_edge(sp1, sp2)

def find_parents(graph, node):
    """
    Get the governing nodes of a given node in the graph
    """
    return graph.reverse()[node].keys()

def find_heads(graph, nodes):
    """
    Returns a list of nodes in the given set of nodes
    which are heads (i.e., don't have any other node as their head)
    """
    return [node for node in nodes
            if not(find_parents(graph, node))]

def safe_pos_tag(sent):
    """
    Safeguards nltk's POS tag from crashing on:
    - Empty words
    """
    proc_sent = [(w if w \
                  else " ")
                  for w in map(asciidammit,
                               sent)]
    return nltk.pos_tag(proc_sent)

def is_noun_tag(nltk_pos_tag):
    """
    Returns True iff the given nltk pos tag
    is a noun
    """
    return nltk_pos_tag.startswith("N")


def is_verb_tag(nltk_pos_tag):
    """
    Returns True iff the given nltk pos tag
    is a verb.
    """
    return nltk_pos_tag.startswith("V")

def is_adverb_tag(nltk_pos_tag):
    """
    Returns True iff the given nltk pos tag
    is a adverb.
    """
    return nltk_pos_tag.startswith("RB")


def is_determiner_tag(nltk_pos_tag):
    """
    Returns True iff the given nltk tag
    is a determiner
    """
    return nltk_pos_tag == "DT"

def is_prepositional_tag(nltk_pos_tag):
    """
    Returns True iff the given nltk tag
    is a preposition
    """
    return nltk_pos_tag == "IN"

def is_modal_tag(nltk_pos_tag):
    """
    Returns True iff the given nltk tag
    is a modal
    """
    return nltk_pos_tag == "MD"

def is_wh_question(nltk_pos_tag):
    """
    Returns True iff the given nltk tag
    is a modal
    """
    return nltk_pos_tag.startswith("W")

def chunk_by_func(ls, classifier_func, chunk_gen_func):
    """
    Chunk a list based on a classifier function
    ls - the list to chunk
    classifier_func - takes an item (index and value) and ls and returns the element classs
    chunk_gen_func - how to represent the chunk in the output
                     takes as input the class (according to classifier_func)
                     and the elements in the chunk
                     returns a list which would be extended in the return function
    """
    ret = []
    cur_chunk = []
    prev_tags = [] # Indicates the last words classes
    for elem_ind, elem in enumerate(ls):
        # Group by the mapped value of each
        # word in the question
        cur_class = classifier_func(elem_ind,
                                    elem,
                                    ls,
                                    prev_tags)

        if prev_tags \
           and (cur_class != prev_tags[-1]):
            # finised a chunk
            ret.extend(chunk_gen_func(prev_tags[-1],
                                      cur_chunk))
            cur_chunk = [elem]
        else:
            # Still appending chunk
            cur_chunk.append(elem)

        prev_tags.append(cur_class)

    if cur_chunk:
        # Add the last chunk
        ret.extend(chunk_gen_func(prev_tags[-1],
                                  cur_chunk))

    return ret

def get_maximum_dist(elem1, elem2):
    """
    Get the maximum distance between
    two lists of indices
    """
    ls = elem1 + elem2
    return max(ls) - min(ls)

def get_dep_dist(elem1,
                 elem2):
    """
    Get the dependency distance between
    two list of indices.
    """
    ret = None
    return max([abs(x - y)
                for x in elem1
                for y in elem2])


def split_consecutive(ls):
    """ Returns a list of consecutive (rising) lists in ls """
    ret = []
    cur = []
    for x in ls:
        if (not cur) or (x == cur[-1] + 1):
            cur.append(x)
        else:
            ret.append(cur)
            cur = [x]
    if cur:
        ret.append(cur)
    return ret

def deep_str(obj, delim= ' '):
    """
    Apply str also for lists
    """
    if isinstance(obj, list):
        return delim.join(map(str,
                              obj))
    return str(obj)


if __name__ == "__main__":
    q = "What did Albert Einstein say ?".split()
    a = "that stupidity is infinite".split()
    sent = "Albert Einstein said that stupidity is infinite".split()
    ls = split_list([-1, 1, 2, -1, 3, -1, -1, 4], -1)
    print ls
    s = "I give you these powers".split()
    phrase = ["I"]
    a = Aligner()
    x = a.align_phrase(s, phrase, align_stopwords = False)
    print non_projective_edge((2, 11), (11, 14))
    print most_common(['goose', 'duck', 'duck'])
