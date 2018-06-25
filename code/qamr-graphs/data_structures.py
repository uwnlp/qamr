# External imports
import pandas as pd
import numpy as np
from collections import defaultdict
import networkx as nx
import logging
from operator import itemgetter
import logging
import random
import pandas as pd
from stop_words import get_stop_words
from unidecode import unidecode
from fuzzywuzzy.utils import asciidammit
from pprint import pformat
from pprint import pprint
import pdb
import itertools

# Local imports
from preproc import find_heads
from preproc import chunk_by_func
from preproc import is_verb_tag
from preproc import is_adverb_tag
from preproc import enum
from preproc import group_consecutive
from spacy_wrapper import spacy_with_whitespace_tokenizer as spacy_ws
from preproc import is_determiner_tag
from preproc import is_prepositional_tag
from preproc import is_modal_tag
from preproc import is_wh_question
from preproc import is_noun_tag

from qa_template_to_oie import NonterminalGenerator
from qa_template_to_oie import OIE
from fuzzywuzzy.utils import asciidammit

class Sentence:
    """
    Container for all words and QA pairs pretaining to a single sentence.
    """
    def __init__(self,
                 sentence,
                 pos_tags,
                 template_extractor,
                 sentence_id):
        """
        sentence - tokenized sentence
        pos_tags - corresponding pos tags
        template_extractor - a Templateextractor instance to accumulate
                             templates across sentences
        """
        # if len(sentence) != len(pos_tags):
        #     pdb.set_trace()
        # assert len(sentence) == len(pos_tags), "sent: {}({})\npos tags:{}({})".format(sentence, len(sentence), pos_tags, len(pos_tags))

        self.sentence = sentence
        self.pos_tags = pos_tags


        self.sentence_id = sentence_id
        self.sentence_str = " ".join(self.sentence)
        self.qa_pairs = []
        self.workers = set()
        self.template_extractor = template_extractor

    # Enum of different possible feature combinations
    features = enum(PREDICATE = 0,
                    POS = 1,
                    DEP = 2,
                    CONST = 3,
                    PASS = 4)

    def to_see_format(self, feats, see_format):
        """
        Returns abisee's story format for this sentence.
        feats is a list of Sentence.features indicating what features
        to include in the output.
        """
        ret = []
        for qa in self.qa_pairs:
            if qa.oie:
                pred_indices = qa.oie.get_sentence_indices()[0]
                if pred_indices:
                    cur_sent = " ".join([self.apply_feats(word,
                                                          word_ind,
                                                          feats,
                                                          pred_indices = pred_indices,
                                                          pos_tags = self.pos_tags)
                                         for word_ind, word
                                         in enumerate(self.sentence)])
                    ret.append("{}\n\n@highlight\n\n{}".format(cur_sent,
                                                               qa.oie.to_see_format(see_format)))
        return ret

    def get_all_implicit_relations(self):
        """
        Get all of the relations between two elements in questions.
        """
        ret = set()
        for qa in self.qa_pairs:
            for c in qa.get_implicit_relations():
                ret.add(c)
        return ret

    def get_all_explicit_relations(self):
        """
        Get all of the relations between elements in answers and respective
        questions.
        """
        ret = set()
        for qa in self.qa_pairs:
            for c in qa.get_explicit_relations():
                ret.add(c)
        return ret

    def get_missing_questions(self):
        """
        Get implicit relations for which there are no explcit
        questions.
        """
        implicit = self.get_all_implicit_relations()
        explicit = self.get_all_explicit_relations()

        ret = []

        for (s1, s2) in map(tuple, implicit):
            s1 = set(s1)
            s2 = set(s2)
            covered = False
            for (s3, s4) in map(tuple, explicit):
                s3 = set(s3)
                s4 = set(s4)
                if (s1.issubset(s3)) and (s2.issubset(s4)) or \
                   (s1.issubset(s4)) and (s2.issubset(s3)):
                    covered = True
                    break

            if not covered:
                ret.append((list(s1), list(s2)))
        return ret


    def apply_feats(self, word, word_ind, feats, **args):
        """
        Apply all requested features on this word.
        """
        ret = word
        # Collect features
        for func in [Sentence.add_pos_feat,
                     Sentence.add_predicate_feat,
                     Sentence.add_pass_feat]:
            ret = func(ret,
                       word_ind,
                       feats,
                       **args)
        return ret

    @staticmethod
    def add_pass_feat(word, word_ind, feats, **args):
        """
        Don't add any feature.
        """
        return word

    @staticmethod
    def add_predicate_feat(word, word_ind, feats, **args):
        """
        Return this word with predicate features, if indicated
        """
        return "(PRED {} )PRED".format(word) \
            if (word_ind in args['pred_indices']) and \
               (Sentence.features.PREDICATE in feats) \
               else word

    @staticmethod
    def add_pos_feat(word, word_ind, feats, **args):
        """
        Return this word with POS features, if indicated
        """
        return "({pos} {word} ){pos}".format(word = word,
                                             pos = args["pos_tags"][word_ind]) \
                                             if Sentence.features.POS in feats \
                                                else word

    def add_qa_pair(self,
                    worker_id,
                    special_word,
                    raw_question,
                    raw_answer,
                    aligned_question,
                    aligned_answer):
        """
        Add a QA pair to this sentence's annotation
        """
        qa = QA_pair(worker_id,
                     special_word,
                     raw_question,
                     raw_answer,
                     aligned_question,
                     aligned_answer,
                     self.pos_tags,
                     self)

#        qa.template_str = self.template_extractor.get_qa_template(qa)
        self.qa_pairs.append(qa)
        self.workers.add(worker_id)

    def consolidate_qas(self):
        """
        Consolidate QAs corresponding to the same question
        Currently chooses the longest answer.
        Done this way to perhaps incoporate more complicated measures at this point.
        """
        consolidated_questions = [] # Keep track of questions already dealt with
        qa_pairs = []
        for qa in self.qa_pairs:
            if qa.raw_question_str in consolidated_questions:
                # already dealt with this question
                continue
            corresponding_qas = self.get_qas_by_question(qa.raw_question_str)
            qa_pairs.append(max(corresponding_qas,
                                key = lambda qa: len(qa.raw_answer_str)))
            consolidated_questions.append(qa.raw_question_str)
        self.qa_pairs = qa_pairs

    def get_qa_pairs(self, num_of_workers):
        """
        Get the qa pairs
        num of workers indicates how many workers to allow (sequntally ordered)
        use -1 if you want all of them
        """
        if num_of_workers == -1:
            return self.qa_pairs
        allowed_workers = list(self.workers)[: num_of_workers]
        return [qa for qa in self.qa_pairs if qa.worker_id in allowed_workers]

    def get_qas_by_question(self, raw_question_str):
        """
        Returns a list of QAs by the raw question they contain.
        """
        return [qa
                for qa
                in self.qa_pairs
                if qa.raw_question_str == raw_question_str]

    def get_num_of_workers(self):
        """
        Get the numer of all of the workers on this sentence
        """
        return len(self.workers)

    def __len__(self):
        """
        Returns the number of QA pairs
        """
        return len(self.qa_pairs)

    def __getitem__(self, i):
        """
        Return the ith question.
        """
        return self.qa_pairs[i]

    def __str__(self):
        return " ".join(self.sentence)

class Edge:
    """
    Container for an edge in the sentence structure
    """
    def __init__(self, sent, src, dst, label, wid):
        """
        Edge from <src> to <dst> with the relation <label>
        Sent (the original sentence) is used for printing purposes
        """
        self.sent = sent
        self.src = src
        self.dst = dst
        self.label = label
        self.wid = wid

    def __str__(self):
        """
        Textual representation of an edge
        """
        return "{} -> {} ({})".format(' '.join(self.sent.sentence[self.src[0] : self.src[1]]),
                                      ' '.join(self.sent.sentence[self.dst[0] : self.dst[1]]),
                                      ' '.join(self.label))

class EdgePossibilities(Edge):
    """
    Container class for uncertain edges - where a question contains
    multiple sentence elements
    """
    def __init__(self, sent, src, dst, label, wid):
        """
        Basically just calls the super class' constructor
        """
        Edge.__init__(self, sent, src, dst, label, wid)

    def __str__(self):
        return "{} -> {} ({})".format(self.src,
                                      self.dst,
                                      ' '.join(self.label))

class QA_pair:
    """
    Container for raw and aligned QA pairs
    """

    # Static spaCy parser instance
    parser = spacy_ws.parser

    def __init__(self, worker_id, special_word, raw_question, raw_answer,
                 aligned_question, aligned_answer, pos_tags, sentence):
        """
        Initialize from the input file format
        pos_tags - pos tags from the original sentence
        """
        self.np_chunk_counter = 0
        self.vp_chunk_counter = 0
        self.advp_chunk_counter = 0
        self.worker_id = worker_id
        self.special_word = special_word
        self.sentence = sentence

        # Normalize questions by removing all question marks
        # (In the dataset some of the questions end with it while other don't)
        self.raw_question = [s
                             for s in raw_question
                             if s != "?"]
        self.raw_question_str = " ".join(raw_question)
        self.aligned_question = [Word_alignment(word, word_index, self.raw_question) for
                                 (word_index, word)
                                 in enumerate([w
                                               for w in aligned_question
                                               if "?" not in w])]
        self.raw_answer = raw_answer
        self.raw_answer_str = " ".join(raw_answer)
        self.aligned_answer = [Word_alignment(word, word_index, self.raw_answer) for
                                 (word_index, word) in enumerate(aligned_answer)]
        self.pos_tags = pos_tags
        self.chunks = {}

        ## Calculated fields
        try:
            self.question_spacy_parse = QA_pair.parser(unicode(self.raw_question_str))

        except Exception as e:
            # Some questions fail when decoding to unicode
            # Store None as parse in those cases
            self.question_spacy_parse = None

#        self.template_question = self.extract_template()
#        self.template_question_str = " ".join([word.source_word
#                                               for word in self.template_question])


    # Enum of different chunk types in questions
    chunk_types = enum(UNMAPPED = 0,
                       MAPPED_VERB = 1,
                       UNMAPPED_VERB = 2,
                       MAPPED_NOUN = 3,
                       UNMAPPED_NOUN = 4,
                       MAPPED_ADV = 5,
                       UNMAPPED_ADV = 6)


    # Words which play special role in questions
    # and we'd like to exempt them from certain handling
    qa_special_words = ["be",
                        "have",
                        "do",
                        "kind",
                        "type"]

    def get_implicit_relations(self):
        """
        Returns a list of list of indexes which appear in
        the question.
        """
        elements = []
        for chunk in self.chunks.values():
            elements.extend(map(tuple,
                                group_consecutive(chunk.get_sentence_indices())))

        return map(frozenset,
                   itertools.combinations(elements,
                                          2))

    def get_explicit_relations(self):
        """
        Get a list of relations between question and answer.
        """
        elements = []
        for chunk in self.chunks.values():
            elements.extend(map(tuple,
                                group_consecutive(chunk.get_sentence_indices())))

        return map(frozenset,
                   [(elem, tuple(map(lambda word: word.target_word_ind,
                                     self.aligned_answer)))
                    for elem in elements])


    @staticmethod
    def is_mapped_type(chunk_type):
        """
        Return True iff the chunk represents a mapped type
        """
        return chunk_type in [QA_pair.chunk_types.MAPPED_VERB,
                              QA_pair.chunk_types.MAPPED_NOUN,
                              QA_pair.chunk_types.MAPPED_ADV]

    @staticmethod
    def is_verb_type(chunk_type):
        """
        Return True iff the chunk represents a verb type
        """
        return chunk_type in [QA_pair.chunk_types.MAPPED_VERB,
                              QA_pair.chunk_types.UNMAPPED_VERB]

    @staticmethod
    def is_adverb_type(chunk_type):
        """
        Return True iff the chunk represents a verb type
        """
        return chunk_type in [QA_pair.chunk_types.MAPPED_ADV,
                              QA_pair.chunk_types.UNMAPPED_ADV]



    def get_spacy_tok(self, aligned_word):
        """
        Return the associated spacy token for a given index
        in the question
        Assumes it's *not* mapped.
        """
#        assert(not aligned_word.is_mapped)
        return self.question_spacy_parse[aligned_word.source_word_ind]

    def get_np_symbol(self):
        """
        Return the appropriate NP symbol
        """
        ret = "NP{}".format(self.np_chunk_counter)
        self.np_chunk_counter += 1
        return ret

    def get_vp_symbol(self):
        """
        Return the appropriate VP symbol
        """
        ret = "VP{}".format(self.vp_chunk_counter)
        self.vp_chunk_counter += 1
        return ret

    def get_advp_symbol(self):
        """
        Return the appropriate VP symbol
        """
        ret = "ADVP{}".format(self.advp_chunk_counter)
        self.advp_chunk_counter += 1
        return ret

    def get_chunk(self, chunk_type, target_words):
        """
        Return an appropriate Chunk
        """

        if QA_pair.is_verb_type(chunk_type):
            source_word = self.get_vp_symbol()

        elif QA_pair.is_adverb_type(chunk_type):
            source_word = self.get_advp_symbol()
        else:
            source_word = self.get_np_symbol()

        ret = Chunk(source_word = source_word,
                    target_words = list(reversed(target_words)))

        self.chunks[ret.source_word] = ret
        return ret

    def chunk(self):
        """
        Chunk by:
        - If word are aligned:
          (1) Whether the words form a consecutive chunk in the original sentence
          (2) Whether the POS (in the question) matches
        - Otherwise
          - Just (1) above
        """
        ret = []
        target_words = []
        prev_chunk_type = None
        cur_chunk_type = None

        if self.question_spacy_parse is None:
            # We'll use the spacy POS, so stop now if not available
            # Doesn't happen often
            return []

        for word_ind, word in reversed(list(enumerate(self.aligned_question))):
            # Chunking in reverse to ease the addition of pre-modifiers to the chunk
            pdb.set_trace()
            cur_pos = self.question_spacy_parse[word_ind].tag_
            cur_lemma = self.question_spacy_parse[word_ind].lemma_

            if is_determiner_tag(cur_pos) and \
               target_words:
                # Add a determiner to any existing chunk
                target_words.append(word)
                continue

            if is_prepositional_tag(cur_pos) or \
               is_modal_tag(cur_pos) or \
               (word_ind == 0) or \
               (cur_lemma in QA_pair.qa_special_words):
                if target_words:
                    # Special words are transffered to the output as is
                    ret.append(self.get_chunk(prev_chunk_type,
                                              target_words))
                    target_words = []
                ret.append(word)
                continue

            # Determine new chunk type
            if word.is_mapped:
                if is_verb_tag(cur_pos):
                    cur_chunk_type = QA_pair.chunk_types.MAPPED_VERB
                elif is_adverb_tag(cur_pos):
                    cur_chunk_type = QA_pair.chunk_types.MAPPED_ADV
                else:
                    cur_chunk_type = QA_pair.chunk_types.MAPPED_NOUN

            else:
                if is_verb_tag(cur_pos):
                    cur_chunk_type = QA_pair.chunk_types.UNMAPPED_VERB
                elif is_adverb_tag(cur_pos):
                    cur_chunk_type = QA_pair.chunk_types.UNMAPPED_ADV
                else:
                    cur_chunk_type = QA_pair.chunk_types.UNMAPPED_NOUN

            if (prev_chunk_type is None) or (not target_words) or \
               ((cur_chunk_type == prev_chunk_type) and \
                ((not QA_pair.is_mapped_type(cur_chunk_type)) or \
                 (target_words[-1].source_word_ind - word.source_word_ind) == 1)):
                # This continues previous chunk
                target_words.append(word)

            else:
                # This is a new chunk
                ret.append(self.get_chunk(prev_chunk_type,
                                          target_words))
                target_words = [word]

            prev_chunk_type = cur_chunk_type

        if target_words:
            ret.append(self.get_chunk(prev_chunk_type,
                                      target_words))

        self.reverse_template_numbers()
        return list(reversed(ret))

    def reverse_template_numbers(self):
        """
        Reverse template elements numbers such that they appear in the
        lexical order.
        """
        nps = [np_chunk
               for np_chunk in self.chunks.iterkeys()
               if np_chunk.startswith("NP")]
        vps = [np_chunk
               for np_chunk in self.chunks.iterkeys()
               if np_chunk.startswith("VP")]
        advps = [chunk
                 for chunk in self.chunks.iterkeys()
                 if chunk.startswith("ADVP")]
        new_chunks = {}

        for ls, prefix in [(nps, "NP"),
                           (vps, "VP"),
                           (advps, "ADVP")]:
            for elem_name in ls:
                new_elem_name = "{}{}".format(prefix,
                                              len(ls) - int(elem_name.replace(prefix, "")) - 1)
                self.chunks[elem_name].source_word = new_elem_name
                new_chunks[new_elem_name] = self.chunks[elem_name]

        self.chunks = new_chunks




    def extract_template(self):
        """
        Extract a template from this question
        """
        return self.chunk()

    def __str__(self):
        # Add a question mark if needed
        return " ".join(["{} {}".format(" ".join(self.raw_question),
                                        "?" if (not self.raw_question[-1].endswith("?"))\
                                        else ""),
                         " ".join(self.raw_answer)])

class Word_alignment:
    """
    Container for word alignment between word in element (question or answer) to
    the original sentence.
    """
    def __init__(self, map_word, source_word_ind, raw_source):

        """
        Map from source word and index to a target word and index.
        Parses the input mapping format: {{target-index target-word}} | {source-word}
        """
        self.source_word_ind = source_word_ind
        self.source_word = raw_source[self.source_word_ind]

        if map_word.startswith("{{") and \
           map_word.endswith("}}"):
            # Parse mapping format
            map_word = map_word.replace("{{", '').replace("}}", '')
            self.target_word_ind, self.target_word = map_word.split("|", 1)
            self.target_word_ind = int(self.target_word_ind)
        else:
            # This word isn't mapped
            self.target_word_ind = -1
            self.target_word = None

        # Indicate whether this word is mapped
        self.is_mapped = (self.target_word is not None)

    def __str__(self):
        # Return the best approximation of this word
        # TODO: See issue regarding the use of inflected words in extractions
        #       in docs/oie.md
        #       This is decided by either using self.source_words
        #       or self.target_words here
        # Another option to go would be to use self.target_word if exists.
        return self.source_word


class Chunk:
    """
    Chunk container class
    """
    def __init__(self,
                 source_word,
                 target_words):
        """
        Simple container
        source_word - chunk name
        target_words - list of Word_alignment instances
        """
        self.source_word = source_word
        self.target_words = target_words


    def get_source_indices(self):
        """
        Return the source indices of all words in this chunk.
        """
        return [word.source_word_ind
                for word
                in self.target_words]

    def get_sentence_indices(self):
        """
        Get a list of sentence word indices participating
        in the element.
        """
        possible_mappings = [word
                             for word
                             in self.target_words
                             if not type(word) is str]

        return [w.target_word_ind
                for w in possible_mappings
                if w.is_mapped]

    def is_mapped(self):
        """
        Return True iff at least one of the words
        in this chunk is mapped.
        """
        return any([word.is_mapped
                    for word in self.target_words])

    def __str__(self):
        """
        Textual representation of this chunk
        """
        return " ".join([str(w) for w in self.target_words])

class Graph_to_amr:
    """
    NetworkX to AMR
    """
    def __init__(self, sent, amr_head):
        """
        sent - a list of words in the sentence
        """
        self.sent = sent
        self.digraph = nx.DiGraph()
        self.var_names = defaultdict(lambda : [])
        self.span_to_var = {}
        self.head = amr_head

    def add_edge(self, u, v, label):
        """
        Add and AMR edge from u to v with the the given label
        will override any previous label between these two nodes
        Both u and v are spans of indexes in sent
        """
        self.digraph.add_edge(u,v)
        self.digraph[u][v]["label"] = label

    def get_var_name(self, span):
        """
        Given a span, return its variable name.
        Will create it if necessary.
        Also returns whether this is a reentry.
        """
        reent = True
        if span not in self.span_to_var:
            self.span_to_var[span] = self.gen_var(span)
            reent = False
        return self.span_to_var[span], reent

    def get_pred_name(self, span):
        """
        Similar to get_var_name,
        but never reentrant
        """
        return self.gen_var(span)

    def get_pseudo_pb_sense(self, span):
        """
        Fake a PropBank span
        """
        return self.get_amr_concept(span)

    def get_amr(self):
        """
        Output AMR format
        """
        # heads = find_heads(self.digraph, self.digraph.nodes())
        # assert len(heads) == 1, "More/less than one head: {}".format(heads)
        # self.head = heads[0]
        return AMR(self.rooted_graph_to_amr(self.head))

    def get_amr_concept(self, span):
        """
        Get concept from sentence span
        """
        # I assume that spaces are invalid
        return "-".join(self.sent[span[0]: span[1]])

    def rooted_graph_to_amr(self, head):
        """
        Recursively create the string of an amr
        TODO: what if there are cycles? should this create the arg-i-of?
        """
        args = [":{} {}".format(self.simplify_label(feats["label"]),
                                self.rooted_graph_to_amr(neighbour))
                for neighbour, feats in self.digraph[head].iteritems()]

        var, reent = (self.get_pred_name(head), False) if args \
                     else self.get_var_name(head)

        concept = self.get_pseudo_pb_sense(head) if args \
                  else self.get_amr_concept(head)

        top = var if reent \
              else "{} / {}".format(var, concept)

        return top if (not args and reent) \
            else "({} {})".format(top,
                                " ".join(args))

    def gen_var(self, span):
        """
        generate a new unique variable name for word
        """
        # Use first letter convention
        let = self.sent[span[0]][0].lower()
        if not let.isalpha():
            let = "na"
        ret = "{}{}".format(let,
                            len(self.var_names[let]))
        self.var_names[let].append(ret)
        return ret

    def simplify_label(self, label):
        """
        Return a single word (hopefully wh-question) representing this label (a list of words)
        to comply with AMR convention.
        """
        opts = [qw for qw in question_words
                if qw.lower() in map(lambda w: w.lower(), label)]
        return opts[0]\
            if opts else label[0]


class TemplateExtractor:
    """
    Container class for template extraction functions
    """
    def __init__(self):
        """
         - Store English stop words
        """
        self.stopwords = get_stop_words("en") + ['?']
        self.vocab = set()
        self.invalid_questions = [] # Questions not passing the filter
        self.template_dictionary = defaultdict(list) # From template to questions

    def is_valid_question(self,
                          aligned_question,
                          parsed_question):
        """
        Returns True iff the given template is a valid relation:
        (1) Includes at least 1 mapped chunk
        (2) all other words are stopwords
        (3) includes at least 1 verb
        """
        return True
        if parsed_question is None:
            # This question failed to parse - return invalid question
            return False

        return \
            (not aligned_question[0].is_mapped) and\
            is_wh_question(parsed_question[0].tag_) and \
            any([word.is_mapped for word in aligned_question]) and \
            all([word.source_word.lower() in self.stopwords
                 for word in aligned_question
                 if (not word.is_mapped)]) and \
            any([is_verb_tag(word.tag_)
                 for word in parsed_question])

    def templatize_spacy_tok(self,
                             tok,
                             non_terminal_generator):
        """
        Return a template version of the given spaCy token.
        Returns None if the token should be omitted in the template.
        """
        tag = tok.tag_

        if is_determiner_tag(tag):
            return None

        if is_prepositional_tag(tag) or \
           is_modal_tag(tag):
            # Template prepositions and modals
            ret =  non_terminal_generator.generate(tag)

        else:
            # Otherwise return the lemma of the word
            ret = non_terminal_generator.generate(tok.lemma_)

        self.vocab.add(ret)
        return ret

    def get_qa_template(self, qa):
        """
        Get a template version (list of tokens) of the given question
        Modifies qa's chunks accordingly
        """
        nt_generator = NonterminalGenerator()
        if not self.is_valid_question(qa.aligned_question,
                                      qa.question_spacy_parse):
            return None

        if qa.question_spacy_parse:
            # Take the lemma from the raw question for all non-mapped elements (= non chunks)
            # Ommit determiners from the question template
            template_ls = []
            for w in qa.template_question:
                if any([w.source_word.startswith(pref)
                        for pref in ["NP", "VP", "ADVP"]]):
                    # this is a chunk - leave as is
                    template_ls.append(w.source_word)
                else:
                    template_word = self.templatize_spacy_tok(qa.get_spacy_tok(w),
                                                              nt_generator)
                    if template_word is not None:
                        template_ls.append(template_word)

                        # Add the interpretation of this template word to the
                        # chunks of this QA
                        qa.chunks[template_word] = Chunk(template_word,
                                                         [w])

            template_str = ' '.join(template_ls)
        else:
            # Else - return the raw template_question
            template_str = qa.template_question_str

        # Normalize by adding a question mark
        # (We make sure that the template wouldn't have a question mark before that)
        template_str += " ?"

        # Record and return
        self.template_dictionary[template_str].append(qa)
        return template_str

    # def extract_templates(self,
    #                       sents,
    #                       output_fn,
    #                       apply_filter,
    #                       lemmatize):
    #     """
    #     1. Filter templates
    #     2. Extract templates with stats to file

    #     @param: apply filter -
    #             bool, controls whether to filter the templates(using self.is_valid_question
    #     @param: lemmatize -
    #             bool, controls whether to lemmatize the template
    #     """
    #     d = defaultdict(list)
    #     for sent in sents:
    #         for qa in sent.qa_pairs:
    #             nt_generator = NonterminalGenerator()
    #             if apply_filter and self.is_valid_question(qa.aligned_question,
    #                                                        qa.question_spacy_parse):
    #                 if lemmatize and qa.question_spacy_parse:
    #                     # Take the lemma from the raw question for all non-mapped elements (= non chunks)
    #                     # Ommit determiners from the question template
    #                     template_ls = []
    #                     for w in qa.template_question:
    #                         if w.is_mapped:
    #                             # this is a chunk - leave as is
    #                             template_ls.append(w.source_word)
    #                         else:
    #                             template_word = self.templatize_spacy_tok(qa.get_spacy_tok(w),
    #                                                                       nt_generator)
    #                             if template_word is not None:
    #                                 template_ls.append(template_word)

    #                                 # Add the interpretation of this template word to the
    #                                 # chunks of this QA
    #                                 qa.chunks[template_word] = Chunk(template_word,
    #                                                                  [w.source_word])

    #                     template_str = ' '.join(template_ls)
    #                 else:
    #                     # Else - return the raw template_question
    #                     template_str = qa.template_question_str

    #                 # Normalize by adding a question mark
    #                 # (We make sure that the template wouldn't have a question mark before that)
    #                 template_str += " ?"

    #                 d[template_str].append(qa)
    #                 qa.template_str = template_str

    #             else:
    #                 # Not a valid question
    #                 self.invalid_questions.append(qa)

    #     ordered_templates = sorted(d.iteritems(),
    #                                key = lambda(s,
    #                                             ls): len(ls),
    #                                reverse = True)

    #     total = sum(map(len,
    #                     map(itemgetter(1),
    #                         ordered_templates))) * 1.0

    #     logging.info("total # of valid questions: {}".format(total))

    #     data = []
    #     cdf = 0
    #     for i, (s, ls) in enumerate(ordered_templates):
    #         cur = len(ls)
    #         cdf += cur
    #         if ((cdf / total) > .8) and \
    #            ((cdf - cur) / total <= .8):
    #             logging.info("CDF .8 reached in {} templates".format(i))

    #         data.append(pd.Series([s,
    #                                round(cur / total, 4),
    #                                round(cdf / total, 4),
    #                                "\n".join(["{} {}\n{}".format(qa.raw_question_str,
    #                                                              qa.raw_answer_str,
    #                                                              pformat([(chunk_name, str(chunk))
    #                                                                       for  chunk_name, chunk
    #                                                                       in qa.chunks.iteritems()]))
    #                                          for qa in random.sample(ls,
    #                                                                  min(len(ls), 3))])]))



    #     df = pd.DataFrame(data)
    #     df.columns = ["Template", "Prob", "CDF", "Examples"]
    #     df.to_csv(out_fn,
    #               header = True,
    #               index = False)

    #     return ordered_templates

def load_sentences(fn):
    """
    Returns a list of sentences as annotated in the input file
    """
    df = pd.read_csv(fn, names = ["WID", "special",
                                  "raw_question", "raw_answer",
                                  "aligned_question", "aligned_answer"])
    ret = []
    cur_sent = None
    template_extractor = TemplateExtractor()
    for row_index, row in df.iterrows():
        if not(isinstance(row["raw_question"], basestring) and \
               isinstance(row["raw_answer"], basestring)):
            # This is a row introducing a new sentence
            if cur_sent:
                cur_sent.consolidate_qas()
                ret.append(cur_sent)

            pos_tags = [word.tag_
                        for word
                        in spacy_ws.parser(unicode(row["WID"],
                                                   encoding = 'utf8'))]

            cur_sent = Sentence(row["WID"].split(" "),
                                pos_tags, # POS tags
                                template_extractor,
                                row["raw_question"]) # = Sentence id
        else:
            # This is a QA pair relating to a previously introduced sentence
            cur_sent.add_qa_pair(row["WID"], row["special"],
                                 row["raw_question"].split(" "), row["raw_answer"].split(" "),
                                 row["aligned_question"].split(" "), row["aligned_answer"].split(" "))
    cur_sent.consolidate_qas()
    ret.append(cur_sent) # Append the last sentence
    return ret


question_words = ["what",
                  "when",
                  "where",
                  "which",
                  "who",
                  "whom",
                  "whose",
                  "why",
                  "how"]

if __name__ == "__main__":
    amr = Graph_to_amr("orange is the new black".split())
    amr.add_edge((0,1),(1,2), "what is my name?".split())
    print amr.get_amr()
