""" Usage:
   chunk --in=INPUT_FILE (--projective | --non-projective) [--single-words] [--html=OUTPUT_DIR] [--psd=PSD_FILE] [--txt=TXT_FILE] [--amr=AMR_FILE] [--concepts=CONCEPTS_FILE] [--triples=TRIPLES_FILE]
"""

from collections import defaultdict
from operator import itemgetter
from data_structures import Sentence, Edge, load_sentences, EdgePossibilities, Graph_to_amr
from preproc import split_list,is_subchunk, uniq, reachable_from, non_projective_edge,\
    intersecting_spans, find_heads, find_parents, graph_to_triples, split_consecutive
import networkx as nx
from cgi import escape
import logging
import os
import pdb
from docopt import docopt
from pprint import pprint
from brat_handler import Brat
from spacy_wrapper import spacy_with_whitespace_tokenizer as spacy_ws
from spacy_wrapper import find_root

# Ideas:
# - Signals for predicates:
#   Appeared inflected (can be identified during alignment)
#   Appear only/mostly in questions
#
# - Somehow take into account the number of edges which are connected to this predicates
#   when calculating its probability (e.g., for given), should be really careful
# - calculate the head of the span after each iteration? Currently we miss some edges because
#   they appear to cross spans - I think that this is what leaves multi-nation corporation orphaned
class QAStructure:
    """
    Identify chunks in a sentence using corresponding QA pairs
    """
    def __init__(self, sent, num_of_workers):
        """
        Init datastructures
        num_of_workers indicates how many to use, use -1 if you want all of them
        """
        self.sent = sent
        self.num_of_workers = num_of_workers
        self.brat_template = open("./brat_template.html").read()
        # store hyperparams which could be fitted on some dev partition
        self.hyperparams = {
            "prob_weight": 1.0,
            "prom_weight": 0
        }
        self.total_edge_count = 0
        self.question_rels_count = 0
        self.parser = spacy_ws.parser

    def process(self, projective, single_words):
        """
        Induce a stucture over the sentence
        """
        self.chunks, self.non_minimal_rels = self.collect_chunks(self.sent)
        self.subchunks_mapping = self.identify_subchunks(self.chunks)
        self.count_chunks = self.count_chunks_occurence(self.non_minimal_rels,
                                                        self.subchunks_mapping)
        self.minimal_rels = self.induce_structure(self.non_minimal_rels,
                                                  self.subchunks_mapping,
                                                  self.count_chunks,
                                                  projective)
        if single_words:
            self.split_to_words()

    def split_to_words(self):
        """
        After constructing the graph, find nodes which spans multiple
        words and break them according to dependency parse.
        """
        # Find nodes which span mulitple words
        multi_word_nodes = [(node_start, node_end)
                            for (node_start, node_end) in self.digraph.nodes()
                            if (node_end - node_start) > 1]

        # Break each of these according to dependency parse
        for (start, end) in multi_word_nodes:
            cur_node = (start, end)
            dep_tree = self.parser(unicode(" ".join(self.sent.sentence[start : end]),
                                           "utf8"))
            root = find_root(dep_tree)
            root_node = (start + root.i,
                         start + root.i + 1)

            # Duplicate edges from the multi-word to its head
            for parent_node in self.digraph.predecessors(cur_node):
                self.digraph.add_edge(parent_node,
                                      root_node,
                                      label = self.digraph[parent_node][cur_node]['label'])

            for child_node in self.digraph.neighbors(cur_node):
                self.digraph.add_edge(root_node,
                                      child_node,
                                      label = self.digraph[cur_node][child_node]['label'])

            # Add dependency edges
            for node in dep_tree:
                if node == root:
                    continue
                if node.dep_ not in ["cc", "prep"]:

                    self.digraph.add_edge((start + node.head.i,
                                           start + node.head.i + 1),
                                          (start + node.i,
                                           start + node.i + 1),
                                          label = ["dep:{}".format(node.dep_)])

        #  Remove all multi word nodes from the graph
        for cur_node in multi_word_nodes:
            self.digraph.remove_node(cur_node)


    def count_chunks_occurence(self, non_minimal_rels, subchunks_mapping):
        """
        Count occurence in questions per *minimal* chunk.
        Returns a dictionary from minimal subchunks to counts.
        Hopefully, this is a strong signal that this is indeed what's being put in a relation
        (and not something that can be replaced with a placeholder).
        """
        ret = defaultdict(lambda: [0, 0])
        for edge in non_minimal_rels:
            for chunk_opt in edge.src:
                for min_dst in subchunks_mapping[chunk_opt]:
                    ret[min_dst][0] += 1
                    ret[min_dst][1] += 1

            # Count occurence in answers
            for min_src in subchunks_mapping[edge.dst]:
                ret[min_src][1] += 1

        return dict(ret)

    def choose_source(self, source_opts, chunk_counts):
        """
        Choose the most probable source option based on counts.
        Can either return a list of one option, or none, if the most probable chunk's probability
        isn't above a threshold.
        """
        if (not source_opts):
            return []
        opts_with_scores = zip(source_opts,
                               map(lambda src: (chunk_counts[src][0] * 1.0) / chunk_counts[src][1],
                                   source_opts))

        top_opt = sorted(opts_with_scores,
                         key = lambda (src, score): score,
                         reverse = True)[0]

        return [top_opt[0]] if (top_opt[1] >= 0.8) else []

    def find_surrounding_span(self, (s1, e1), nodes):
        """
        Given a node (s1, e1), and a list of nodes,
        find the smallest node in nodes which contains (s1, e1)
        """
        # Start with the maximal possible spans
        ret = (0, len(self.sent.sentence))
        for (s2, e2) in nodes:
            if (s1 == s2) and (e1 == e2):
                continue
            if (s1 >= s2) and (e1 <= e2):
                # This span contains the given node
                if (e2 - s2) < (ret[1] - ret[0]):
                    # This span is smaller than previously found span
                    ret = (s2, e2)
        return ret


    def check_containment(self, edges, nodes):
        """
        Check that for all edges it holds that the source and target are in the same surrounding
        span.
        """
        return all([self.find_surrounding_span(edge.src, nodes) == self.find_surrounding_span(edge.dst, nodes)
                    for edge in edges])

    def check_projective(self, ((s1, e1), (s2, e2)), edges):
        """
        Returns true iff the given edge doesn't violate projectivity in the
        context of previous edges.
        """
        # We can take any point in the edge, since they do not cross (TODO: I think)
        return  all([not non_projective_edge((s1, s2),
                                            (edge.src[0], edge.dst[0])) for edge in edges])

    def check_intersection(self, new_node, nodes):
        """
        Returns true iff the given edge doesn't cross any of the prvious nodes
        """
        return all([not intersecting_spans(new_node, (node[0], node[1])) for node in nodes])

    def get_possible_edges(self):
        """
        returns a list of [(answer edges, question edges)]
        sorted by the probability of the source node as predicate their src as a predicate
        """
        ret = []
        for (src, chunk_text, chunk_prob) in self.get_chunk_prob():
            # collect all edges in which source participates as a possible source
            cur_d = {"answer_edges": [],
                     "question_edges": []}
            cur_edges = [rel for rel in self.non_minimal_rels if src in rel.src]
            # answer edges
            nodes = set([])
            for dst, label in sorted([(edge.dst, edge.label) for edge in cur_edges],
                                     key = lambda ((start, end), _): end - start):
                if self.check_intersection(src, nodes) and \
                   self.check_intersection(dst, nodes) and \
                   (not intersecting_spans(src, dst)):
                    nodes.add(src)
                    nodes.add(dst)
                    cur_d["answer_edges"].append(Edge(sent = self.sent,
                                                      src = src,
                                                      dst = dst,
                                                      label = label))
            ret.append(self.populate_graph(cur_d["answer_edges"]))
            self.non_minimal_rels = [rel
                                     for rel in self.non_minimal_rels if rel not in cur_edges]
        return ret

    def induce_structure(self, non_minimal_rels, subchunks_mapping, chunk_counts,
                         projective):
        """
        Induce sentence structure (list of edges) from a edges over non-minimal spans
        and the mapping from chunks to subchunks.
        """
        self.edges = []
        self.nodes = set()
        self.amr_head = None
        non_minimal_rels = self.non_minimal_rels
        for (src, chunk_text, chunk_prob) in self.get_chunk_prob():
            # collect all edges in which source participates as a possible source
            cur_edges = [rel for rel in non_minimal_rels if src in rel.src]
            for dst, label, wid, origin in sorted([(edge.dst, edge.label, edge.wid, "answer") for edge in cur_edges] +\
                                                  [(cur_src, edge.label, edge.wid, "question")
                                                   for edge in cur_edges
                                                   for cur_src in edge.src if cur_src != src],
                                                  key = lambda ((start, end), l, w, o): end - start,
                                            reverse = True):
                pos_edge = Edge(sent = self.sent,
                                src = src,
                                dst = dst,
                                label = label,
                                wid = wid)
                if ((not projective) or self.check_projective((src, dst), self.edges)) and \
                   self.check_intersection(src, self.nodes) and \
                   self.check_intersection(dst, self.nodes) and \
                   (not intersecting_spans(src, dst)) and \
                   self.check_containment(self.edges +
                                          [pos_edge],
                                          self.nodes.union([src, dst])):

                    if self.amr_head is None:
                        self.amr_head = src
                    self.total_edge_count += 1

                    pos_edge.label = list(set([" ".join(pos_edge.label)] + map(lambda rel: " ".join(rel),
                                                                               self.find_direct_relation(src, dst, self.non_minimal_rels))))

                    # if origin == "question":
                    #     # This is a relation inferred through cooccurance in the question
                    #     # Try to find a direct similar relation in some other relation
                    #     new_labels = self.find_direct_relation(src, dst, self.non_minimal_rels)
                    #     if new_labels:
                    #         # Found a direct relation
                    #         pos_edge.label = new_labels[0] # TODO: smarter choosing of the appropriate label?
                    #         self.question_rels_count += 1
                    #     else:
                    #         # Didn't find a direct relation - keep the label and log this instance
                    #         pos_edge.label = ["MISSING","("] + pos_edge.label + [")"]
                    # else:
                    #     self.question_rels_count += 1

                    self.edges.append(pos_edge)
                    self.nodes.add(src)
                    self.nodes.add(dst)

            non_minimal_rels = [rel
                                for rel in non_minimal_rels if rel not in cur_edges]

        # Get an undirected graph
        self.graph = self.populate_graph(self.edges)

        # Separate Predicate-Argument Structures
        self.pas = self.split_graph_by_pred(self.graph)

        # Get structured representation
        self.digraph = self.resolve_heads(self.graph)
#        self.amr = self.get_amr(self.digraph)


    def find_direct_relation(self, src, dst, possible_rels):
        """
        Query whether their is a direct QA relation in possible rels between src and dst
        If there is - Return the list of possible labels
        otherwise - returns an empty list
        """
        ret = []
        for edge in possible_rels:
            if (self.find_predicate(edge) == src) and (dst == edge.dst):
                ret.append(edge.label)
        return ret

    def find_predicate(self, possible_edge):
        """
        Return the most probable predicate in a given possible edge
        """
        sorted_chunks = map(itemgetter(0),
                            self.get_chunk_prob())
        opts =  sorted([s for s in possible_edge.src if s in sorted_chunks],
                      key = lambda s: sorted_chunks.index(s))
        if opts:
            return opts[0]
        else:
            return None

    def split_graph_by_pred(self, graph):
        """
        Returns a list of graphs by predicate probability
        """
        ret = []
        for (src, chunk_text, chunk_prob) in self.get_chunk_prob():
            if src not in graph.nodes():
                continue
            cur_graph = nx.DiGraph()
            for (neighbour, feats) in graph[src].iteritems():
                cur_graph.add_edge(src, neighbour)
                cur_graph[src][neighbour]["label"] = feats["label"]
                cur_graph[src][neighbour]["wids"] = feats["wids"]
            if cur_graph.edges():
                ret.append(cur_graph)
        return ret

    def resolve_heads(self, graph):
        """
        For each super span, find sub-spans that
        don't have an head (within that span), connect them to the head of the
        superspan
        """
        # * How will this look like when we have more than one "head"?
        # * How will this look like when we have more than one head for the subspan?
        # Do any of these happen?
        ret = graph.copy()
        for super_span in graph.nodes():
            sub_spans = [n for n in self.subchunks_mapping[super_span]
                         if (n in ret.nodes()) and (n != super_span)]
            if sub_spans:
                # This is indeed a super span -
                # find heads and attach to the parents of the super_span
                heads = find_heads(ret, sub_spans)
                for super_parent in find_parents(ret, super_span):
                    cur_label = ret[super_parent][super_span]['label']
                    for head in heads:
                        ret.add_edge(super_parent, head)
                        ret[super_parent][head]['label'] = cur_label
                ret.remove_node(super_span)
        return ret


    def populate_graph(self, edges):
        """
        Populate the graph of this instance one edge at a time.
        The label is an accumulation of all questions.
        """
        graph = nx.Graph()
        for edge in edges:
            # Add edge - if it aready exists, nothing would change
            # Record the direction of the edge
            graph.add_edge(edge.src, edge.dst)
            graph[edge.src][edge.dst]["wids"] = [edge.wid]

            # Add the question to the list of possible questions
            # TODO:
            #       * only draw edges that appear more than X times, and you can use the "label"
            #         feature as counter.
            graph[edge.src][edge.dst]["opts"] = graph[edge.src][edge.dst].get("opts",[]) +\
                                                [(edge.src, edge.dst, edge.label)]
        return self.disambiguate_graph(graph)

    def get_chunk_prob(self):
        """
        Returns the chunks of this sentence with their associated predicate probability
        sorted from the most to least probable
        """
        return sorted([((start, end),
                        " ".join(self.sent.sentence[start:end]),
                        self.get_chunk_score(num, denom))
                       for (start, end), (num, denom) in sorted(self.count_chunks.iteritems(),
                                                                key = lambda ((start, end), (num, denom)): start)],
                      key = lambda (chunk, text, prob): prob,
                      reverse = True)

    def get_chunk_score(self, num, denom):
        """
        Return the chunk score given the numenator and denom
        from the chunk prob.
        """
        # Prob * prominence
        prob = ((num * 1.0) / denom)
        prom = ((num * 1.0)/ len(self.sent.sentence))
        return (prob * self.hyperparams["prob_weight"]) + (prom * self.hyperparams["prom_weight"])

    def identify_subchunks(self, chunks):
        """
        Calculates a dictionary from chunk to its
        minimal subchunks (a flat list)
        """
        # Sort chunks by their length (in tokens)
        sorted_chunks = sorted(chunks, key = lambda (x, y): y-x)
        ret = dict([(c, set()) for c in chunks])
        for i, c1 in enumerate(sorted_chunks):
            # For each chunk, calculate all subchunks -
            # these have to appear before it in the sorted list
            cur_subs = set()
            for j, c2 in enumerate(sorted_chunks[:i]):
                if is_subchunk(c2, c1):
                    cur_subs = cur_subs.union(ret[c2]) if ret[c2] \
                        else cur_subs.union([c2])

            ret[c1] = list(cur_subs)

        # Augment minimal spans by pointing to themselves
        return dict([(k, v if v else [k]) for k, v in ret.iteritems()])

    def collect_chunks(self, sent):
        """
        Given the input from aligned experiment file pretaining to a single sentence, returns
        tuples consisting of (index-for-start, index-for-end) and a list of relations between chunks.
        """
        ret = []
        non_minimal_rels = []
        for qa in sent.get_qa_pairs(self.num_of_workers):
            # logging.debug("Q: {}".format(" ".join(qa.raw_question)))
            # logging.debug("A: {}".format(" ".join(qa.raw_answer)))

            # Split answer chunks, assumed to be a single consecutive span
            ans_inds = [w.target_word_ind for w in qa.aligned_answer
                        if w.target_word_ind != -1]
            if not ans_inds:
                logging.warn("Empty answer: {}".format(qa))
                continue

            answer_span = self.chunk_to_span(ans_inds)
            ret.append(answer_span)

            # Split question chunks, not necessarily single consecutive like the answer
            question_chunks = split_list([w.target_word_ind for w in qa.aligned_question],
                                         sep = -1)

            # Merge question and answer spans
            question_spans = []
            for ls in question_chunks:
                question_spans.extend(map(lambda x: self.chunk_to_span(x),
                                           split_consecutive(ls)))

            non_minimal_rels.append(EdgePossibilities(sent = sent,
                                                      src = question_spans,
                                                      dst = answer_span,
                                                      label = qa.raw_question,
                                                      wid = qa.worker_id))

            ret.extend(question_spans)

        return list(set(ret)), non_minimal_rels

    def get_edge(self, edge_opts):
        """
        Returns (source, dest, label) from a given list of all of the observed options
        Determines direction of edge and label.
        """
        # Return the an arbitrary edge containing the most common source
        # and therefore the most common destination, the label is arbitrary,
        # but matches the direction
        possible_src = map(itemgetter(0), edge_opts)
        return sorted(edge_opts, key = lambda src: possible_src.count(src), reverse = True)[0]

    def disambiguate_graph(self, graph):
        """
        Choose the most probable option for the graph
        assumes that graph holds all of the current options for the graph
        """
        ret = nx.DiGraph()
        for (u, v) in graph.edges():
            (src, dst, label) = self.get_edge(graph[u][v]["opts"])
            ret.add_edge(src, dst)
            ret[src][dst]["label"] = label
            ret[src][dst]["wids"] = graph[u][v]["wids"]
        return ret

    def get_amr(self, digraph):
        """
        Get an AMR instance
        """
        amr = Graph_to_amr(self.sent.sentence, self.amr_head)
        for u, v in digraph.edges():
            amr.add_edge(u, v, digraph[u][v]["label"])
        return amr.get_amr()

    def get_single_pas_text(self, digraph):
        """
        Get the textual represenation of a single pas encoded in digraph
        """
        # Assert digraph indeed encodes a single pas
        preds = list(set(map(itemgetter(0), digraph.edges())))
        assert len(preds) == 1, "digraph encodes more than one pas"
        pred = preds[0]

        # Get the textual representation, order by the linear oredering of the arguments
        # in the original sentence
        return "\t".join([str(self.sent)] +
                         [" ".join(self.sent.sentence[pred[0]: pred[1]])] + ["{} {}".format(pred[0], pred[1])] +
                         map(str,
                             ['\t'.join([" ".join(feats["wids"]),
                                         " ".join(feats["label"]),
                                         " ".join(self.sent.sentence[start: end]),
                                         "{0} {1}".format(start,end)])
                              for ((start, end), feats) in sorted(digraph[pred].iteritems(),
                                                              key = lambda x: x[0][0])]))

    def get_text_pas(self, digraphs):
        """
        Visualize this structure using textual represntation
        output to the given fileaname.
        """
        return '\n'.join([self.get_single_pas_text(g)
                                for g in digraphs])

    def output_brat_html(self, fn, digraphs):
        """
        Visualize this structure using brat
        output to the given fileaname.
        """
        if not digraphs:
            logging.warn("Empty graphs: {}".format(self.sent))
            self.html = "{}<br>NO DATA".format(self.sent)
        else:
            brat_input = []
            for digraph in digraphs:
                entities = ",\n".join(["['A{start}_{end}', '{label}', [[{start}, {end}]]]".\
                                       format(start = char_start,
                                              end = char_end,
                                              label = "Argument" if \
                                              not digraph.neighbors(node) else \
                                              "Predicate")
                                       for node, (char_start, char_end)
                                       in zip(digraph.nodes(),
                                              map(lambda node: self.word_to_char(node),
                                                  digraph.nodes()))])

                rels = ",\n".join(["['R{}', '{}', [['head', '{}'], ['dep', '{}']]]".format(i,
                                                                                           label,#.split(" ")[0],
                                                                                           self.get_brat_name(src),
                                                                                           self.get_brat_name(dst))
                               for i, ((src, dst), label) in
                                   enumerate(map(lambda (source, dest, label): ((self.word_to_char(source),
                                                                            self.word_to_char(dest)),
                                                                           escape(label.replace("'", r"\'"))),
                                                 [(source, dest, digraph[source][dest]["label"][0])
                                                  for (source, dest) in digraph.edges()]
                                   ))])
                brat_input.append((entities, rels))
            self.html = Brat.get_brat_html(str(self.sent), brat_input)

        with open(fn, 'w') as fout:
            fout.write(self.html)

        logging.debug("output written to: {}".format(fn))

    def choose_label(self, label_list):
        """
        Choose the most appropriate label out of a given list
        """
        return " ".join(label_list[-1])

    def get_brat_name(self, node):
        """
        Get brat name for a given node
        """
        return "A{}_{}".format(*node)

    def word_to_char(self, (word_start, word_end)):
        """
        Given a tuple node indicating word start and end indices,
        return its corresponding char indices
        """
        word_end = word_end -1
        return (word_start + sum(map(len, self.sent.sentence[: word_start])),
                word_end + sum(map(len, self.sent.sentence[: word_end])) + len(self.sent.sentence[word_end]))

    def chunk_to_span(self, chunk):
        """
        Given a list of consecutive indices, returns a 2-tuple
        representing the minimum and maximum (+1) indices
        """
        minVal = min(chunk)
        maxVal = max(chunk) + 1
        return (minVal, maxVal)

def get_text_from_span(s, (start, end)):
    """
    Return the text from a given indices of text (list of words)
    """
    return " ".join(s[start: end])


def process_sent(sent, projective, single_words, num_of_workers):
    """
    Instantiate and process a single sentence
    """
    ret = QAStructure(sent, num_of_workers)
    ret.process(projective, single_words)
    return ret

def calculate_macro(vals, totals):
    """
    Returns the percent of non-direct relations out of the total number of relations.
    """
    total = sum(totals)
    part = sum(vals)
    return (sum(vals) * 1.0) / sum(totals)


def calculate_micro(vals, totals):
    """
    Returns the percent of non-direct relations out of the total number of relations.
    """
    non_empty = [(v, total) for (s, total) in zip(vals, totals) if total]
    return (sum([(v * 1.0) / total
                 for v, total in non_empty]) * 1.0) / len(non_empty)

def plot_indirect(sents, all_fn, direct_fn):
    """
    Plot micro / macro indirect by num of workers
    """
    max_workers = max([s.get_num_of_workers() for s in sents])
    macro_direct = []
    macro_all = []

    totals = [process_sent(sent, -1).total_edge_count
              for sent in sents]
    logging.debug("totals = {}".format(totals))

    for num_of_workers in range(1, max_workers + 1):
        logging.debug("calculating %indirect for {} workers".format(num_of_workers))
        processed_sents = []
        for i, sent in enumerate(sents):
            s = process_sent(sent, num_of_workers)
            processed_sents.append(s)

        macro_direct.append(calculate_macro([s.question_rels_count for s in processed_sents],
                                            totals))
        macro_all.append(calculate_macro([s.total_edge_count for s in processed_sents],
                                         totals))

    logging.info("all = {}\n direct = {}".format(macro_all, macro_direct))

    for (fn, ls) in [(direct_fn, macro_direct), (all_fn, macro_all)]:
        with open(fn, 'w') as fout:
            fout.write("workers\tindirect\n")
            for (num_of_workers, indirect) in enumerate(ls):
                fout.write("{0}\t{1}\n".format(num_of_workers + 1, indirect))

    return macro_direct, macro_all


def get_psd_from_graph(sent):
    """
    Returns a textual format PSD conll represntation
    of the given nx graph.
    """
    digraph = sent.digraph
    words = sent.sent.sentence

    # Get all predicates in the graph, linerally sorted
    sorted_preds = sorted([node for node in digraph.nodes()
                           if digraph.neighbors(node)],
                          key = itemgetter(0))

    # Parse for POS
    parsed_sent = spacy_ws.parser(unicode(sent.sent.sentence_str,
                                          "utf8"))

    # Format output and return
    return '\n'.join(["\t".join([str(word_ind + 1),
                                 word.text,
                                 word.lemma_,
                                 word.tag_,
                                 "+" if is_psd_head(digraph,
                                                    word_ind) else \
                                 "-",
                                 "+" if is_psd_pred(digraph,
                                                    word_ind) else \
                                 "-",
                                 "_"]  + \
                                ["|||".join(map(lambda word: word.decode("utf8"),
                                              digraph[pred][(word_ind, word_ind + 1)]["label"])) if \
                                 (word_ind, word_ind + 1) in digraph.neighbors(pred) else \
                                 "_"
                                 for pred in sorted_preds])
                      for (word_ind, word)
                      in enumerate(parsed_sent)])


def is_psd_head(digraph, word_ind):
    """
    Return True iff word_ind is a head
    of this PSD graph
    """
    node = (word_ind,
            word_ind + 1)

    return (node in digraph) \
        and (not digraph.predecessors(node))


def is_psd_pred(digraph, word_ind):
    """
    Return True iff word_ind is a predicate
    of this PSD grah
    """
    node = (word_ind,
                word_ind + 1)

    return (node in digraph) and \
        len(digraph.neighbors(node)) > 0


def main(fn, projective, single_words, text_format, html_format,
         amr_format, concepts_format, triples_format, psd_format):
    """
    Parse QA annotation of sentences and output pred-args structures
    """
    processed_sents = []

    for i, sent in enumerate(sents):
        logging.debug("{} / {}".format(i+1, len(sents)))
        s = process_sent(sent,
                         projective,
                         single_words,
                         -1)
        processed_sents.append(s)

    if concepts_format:
        fn = concepts_format
        with open(fn, 'w') as fout:
            fout.write("\n".join(["{}\t{}".format(s.sent,
                                                  '\t'.join([" ".join(s.sent.sentence[span[0]: span[1]])
                                                             for span in s.count_chunks]))
                                  for s in processed_sents
            ]))

        logging.debug("writing output to {}".format(fn))

    if triples_format:
        fn = triples_format
        with open(fn, 'w') as fout:
            for s in processed_sents:
                for triple in graph_to_triples(s.digraph, s.sent.sentence):
                    fout.write("{0}\t{1}\n".format(s.sent, triple))


    if text_format:
        fn = text_format
        with open(fn, 'w') as fout:
            fout.write("\n\n".join([s.get_text_pas(s.pas)
                                    for s in processed_sents]))
        logging.debug("writing output to {}".format(fn))

    if html_format:
        base_dir = html_format
        with open(os.path.join(base_dir, "index.html"), 'w') as fout:
            for i, s in enumerate(processed_sents):
                s.output_brat_html(os.path.join(base_dir, "{}.html".format(i)), [s.digraph])
                fout.write("{}{}</a><br><br>\n".format("<a href = {}.html target=_blank>".format(i)
                                                       if processed_sents[i].digraph
                                                       else "[EMPTY] ",
                                                       s.sent))
    if psd_format:
        fn = psd_format
        with open(fn, 'w') as fout:
            for sent_ind, sent in enumerate(processed_sents):
                psd = get_psd_from_graph(sent).encode("utf8")
                fout.write("#{}\n{}".format(sent.sent.sentence_id,
                                            psd) + "\n\n")
        logging.debug("Wrote PSD output to {}".format(fn))

    if amr_format:
        fn = amr_format
        with open(fn, 'w') as fout:
            fout.write("\n\n".join(["# ::snt {}\n{}".format(s.sent, s.amr)
                                    for s in processed_sents]))

    return processed_sents

if __name__ == "__main__":
    args = docopt(__doc__)
    dummy_file = "./testing/out.csv"
    fn = args["--in"]
    sents = load_sentences(fn)
    projective = args["--projective"]
    single_words = args["--single-words"]
    txt_format = args["--txt"]
    html_format = args["--html"]
    amr_format = args["--amr"]
    psd_format = args["--psd"]
    concepts_format = args["--concepts"]
    triples_format = args["--triples"]

    procs = main(fn,
                 projective,
                 single_words,
                 txt_format,
                 html_format,
                 amr_format,
                 concepts_format,
                 triples_format,
                 psd_format)
