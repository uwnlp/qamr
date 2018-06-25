""" Usage:
    qa_template_to_oie --in=INPUT_FILE --out=OUTPUT_FILE

Convert question tempalte to OIE based on predefined rules.
"""
# External imports
from docopt import docopt
import pandas as pd
from collections import defaultdict
from copy import copy
import logging
import pdb
import re
logging.basicConfig(level = logging.DEBUG)

class QAtoOIE:
    """
    Convert QA templates to Open IE extractions
    """
    def __init__(self,
                 template_csv_filename):
        """
        @param template_csv_filename - string, a filename containing mapping from
                                       template to OIE tuples.
        """
        self.template_to_oie = self.load_template_file(template_csv_filename)
        self.memo = {} # memoization of templates

    def load_template_file(self,
                           template_csv_filename):
        """
        Load a template file and returns a dictionary
        """
        df = pd.read_csv(template_csv_filename,
                         sep = ',',
                         header = 0,
                         quotechar= '"',
                         comment = '#')

        return dict(zip(df["Template"],
                        df["OIE"]))

    def template_exists(self,
                        template):
        """
        Returns true iff the template exists in this instance
        """
        return template in self.template_to_oie

    def convert_qa(self,
                   template,
                   qa):
        """
        Given a template string and a matching QA object,
        map the template to the corresponding Open IE tuple and replace chunk names
        with the values in the specific QA.
        """
        oie_string = self.template_to_oie[template]
        return self.instasiate_oie_with_qa(oie_string,
                                           qa)


    def convert_qa_by_question_parse(self,
                                     template_str,
                                     qa):
        """
        Bypass conversion file and try to extract OIE template
        from the structure of the question template.
        If fails to convert, returns None
        """
        if template_str in self.memo:
            # Return from memoization, but replace with
            # the current QA when not None
            ret = self.memo[template_str]
            if ret is not None:
                ret = copy(ret)
                ret.qa = qa
            return ret

        if self.template_exists(template_str):
            # Hand written exceptions
            logging.debug("Found template in file: {}".format(template_str))
            oie_string = self.template_to_oie[template_str]
            ret = OIE(template_str,
                      qa,
                      mode = "MANUAL")
            elements = oie_string.split(' ; ')

            # Assuming predicate appears in the second slot of the template
            ret.set_predicate(elements[1])
            for arg_index, arg in enumerate([elements[0]] + elements[2:]):
                ret.add_arg(arg,
                            arg_index)

            self.memo[template_str] = ret

            return ret

        logging.debug("Parsing automatically: {}".format(template_str))

        # Init return variables
        oie = OIE(template_str,
                  qa,
                  mode = "AUTOMATIC")
        template_tok = template_str.split(" ")
        wh_question = template_tok[0]
        if any([q in wh_question
                for q in ["how", "which", "why"]]):
            # Ignore certain type of infrequent question types
            return None

        predicate_indices = self.identify_predicate_elements(qa,
                                                             template_tok,
                                                             qa.chunks)

        predicate_indices_wo_prep = [ind
                                     for ind in predicate_indices
                                     if "IN_" not in  template_tok[ind]]
        if not(predicate_indices_wo_prep):
            logging.warn("no predicate found: {}".format(template_str))
            self.memo[template_str] = None
            return None

        end_of_pred = max(predicate_indices_wo_prep)

        elements_before_pred = []
        elements_after_pred = []

        for elem_ind, elem in enumerate(template_tok):
            if self.is_element_chunk(elem) and \
               elem_ind not in predicate_indices:
                # this is an element - record its position with relation
                # to the predicate
                if (elem_ind < end_of_pred):
                    elements_before_pred.append(elem)
                else:
                    elements_after_pred.append(elem)

        # Deal with
        # What NP0 VP0 NP1? ANSWER
        # What country was regulating the water level ? the Netherlands
        # -> (The Netherlands; was regulating; the water level)
        if wh_question == "what_0" and template_tok[1] == "NP0":
            elements_before_pred = []

        pred = [template_tok[ind]
                for ind
                in predicate_indices]

        subj = " ".join(map(elementify,
                            elements_before_pred))
        obj = " ".join(map(elementify,
                           elements_after_pred))

        if elements_before_pred:
            # There's already a subject - just add the answer at the end
            if not obj.strip():
                obj = elementify("ANSWER")
            else:
                obj += " ; " + elementify("ANSWER")
        else:
            # Determine subject
            if ("when" in wh_question) or \
               ("where" in wh_question):
                # For when / where questions - posit the answer in the second argument
                # and move the first element from after the predicate to the subject position
                subj = obj
                obj = elementify("ANSWER")

            else:
                # Posit the answer at the subject slot
                if not subj.strip():
                    subj = elementify("ANSWER")
                else:
                    subj += " ; " + elementify("ANSWER")

        oie.add_arg(subj,
                    0)

        oie.set_predicate(" ".join([elementify(template_tok[ind])
                                    for ind
                                    in predicate_indices]))

        oie.add_arg(obj,
                    1)

        self.memo[template_str] = oie

        return oie

    def is_element_chunk(self,
                         chunk):
        """
        Return True iff this is an element in the OIE tuple
        """
        return ("NP" in chunk) or \
            ("IN" in chunk) or \
            ("-PRON-" in chunk) or \
            ("DT" in chunk) or \
            ("ADVP" in chunk)

    def identify_predicate_elements(self,
                                    qa,
                                    template,
                                    chunks):
        """
        Returns list of indices
        which should should be in the predicate slot
        """
        wh_question = template[0]
        # Start by collecting all verbs and prepositions
        # at the end of the question, just before the question
        # mark.
        ret =  [elem_ind
                for (elem_ind,
                     elem) in enumerate(template)
                if (elem.startswith("VP")) or \
                ("have" in elem) or \
                ("kind" in elem) or \
                ("type" in elem) or \
                ((("IN" in elem) or ("about_" in elem)) and \
                 (elem_ind == len(template) - 2))]

        if ret:
            # extend to nouns in case there's no verb and ends with prep
            if ("IN" in template[ret[-1]] or ("about" in template[ret[-1]])) and \
                not any(["VP" in template[elem_ind]
                         for elem_ind in ret]) and \
                             "where" not in wh_question:
                ret = ret[:-1] + [ret[-1] - 1] + [ret[-1]]

            # Extend to form consecutive predicates:
            ret = range(min(ret),
                        max(ret) + 1)

        # add modals
        ret += [elem_ind
                for (elem_ind,
                     elem) in enumerate(template)
                if ("MD" in elem)]

        if ret and qa.question_spacy_parse:
            # Add non-mapped adverbials
            parse = qa.question_spacy_parse
            pred_question_indices = [ind
                                     for pred_ind in ret
                                     for ind
                                     in chunks[template[pred_ind]].get_source_indices()]

            advps = [(elem_ind, elem)
                     for (elem_ind,
                          elem) in enumerate(template)
                     if elem.startswith("ADVP")]

#            pdb.set_trace()

            for advp_ind, advp in advps:
                if any([(parse[tok_ind].head.i in pred_question_indices)
                        for tok_ind
                        in chunks[advp].get_source_indices()]):
                    if advp_ind not in ret:
                        ret.append(advp_ind)



        ret = sorted(ret)

        # add be
        be_elements = [elem_ind
                      for (elem_ind,
                           elem) in enumerate(template)
                      if ("be" in elem)]
        if (len(be_elements) == 2) and \
           "being" not in [be_word
                           for be_elem in [template[ind]
                                           for ind in be_elements]
                           for be_word in chunks[be_elem].target_words]:
            # hack to deal with ordering of is investingating is
            ret = [be_elements[0]] + ret + [be_elements[1]]
        else:
            ret += be_elements
            ret = sorted(ret)

        if all(["be_" in template[elem_ind]
                for elem_ind in ret]) and \
                    any(["do_" in elem
                         for elem in template]):
                        # indicates hiding of predicate
            ret = []

        if ret and qa.question_spacy_parse:
            # Add non-mapped adverbials
            parse = qa.question_spacy_parse
            pred_question_indices = [ind
                                     for pred_ind in ret
                                     for ind
                                     in chunks[template[pred_ind]].get_source_indices()]

            advps = [(elem_ind, elem)
                     for (elem_ind,
                          elem) in enumerate(template)
                     if elem.startswith("ADVP")]

#            pdb.set_trace()

            for advp_ind, advp in advps:
                if any([(parse[tok_ind].head.i in pred_question_indices) and \
                        (parse[tok_ind].dep_ == "advmod") and \
                        (abs(tok_ind - parse[tok_ind].head.i) <= 2)
                        for tok_ind
                        in chunks[advp].get_source_indices()]):
                    if advp_ind not in ret:
                        ret.append(advp_ind)



        return ret

def elementify(tok):
    """
    Return a chunk represntation of this token
    """
    return "{{{}}}".format(tok)


class NonterminalGenerator:
    """
    Generate symbols for non-terminals
    """
    def __init__(self):
        """
        Reset internal state.
        """
        self.non_terminals = defaultdict(lambda : 0)

    def generate(self,
                 symbol):
        """
        Return the next non terminal for this symbol
        """
        ret =  "{}_{}".format(symbol,
                             self.non_terminals[symbol])
        self.non_terminals[symbol] += 1
        return ret


class OIE:
    """
    Represent an OIE extraction
    """
    def __init__(self,
                 template_str,
                 qa,
                 mode):
        """
        template_str - The templetize version of the question
        qa - QA_Pair
        mode - indicating how this OIE was converted (automatic or manual)
        """
        self.template_str = template_str
        self.qa = qa
        self.predicate = None
        self.oie = None
        self.args = {}
        self.mode = mode

    def get_ordered_arg_list(self):
        """
        Convert a dictionary to list of arguments
        """
        return [arg
                for (pos, arg)
                in sorted(self.args.iteritems(),
                          key = lambda (pos, arg): pos)]

    def add_arg(self,
                arg,
                pos):
        """
        set argument at a given position
        """
        self.args[pos] = arg

    def set_predicate(self,
                      pred):
        """
        Set this OIE's predicate
        """
        self.predicate = pred

    def get_template(self):
        """
        Get this OIE's templated format
        """
        # Subject must exist
        assert (0 in self.args)
        sorted_args = self.get_ordered_arg_list()
        return " ; ".join([sorted_args[0],
                           self.predicate] + \
                          sorted_args[1:])

    def get_sentence_indices(self):
        """
        Get the indices of the words participating in this OIE
        """
        indices_dict = dict([(elementify(chunk_name),
                              chunk.get_sentence_indices())
                             for (chunk_name, chunk)
                             in self.qa.chunks.iteritems()])

        indices_dict[elementify("ANSWER")] = [w.target_word_ind
                                              for w in
                                              self.qa.aligned_answer
                                              if w.is_mapped]
        ret = []
        for element in [self.predicate] + self.get_ordered_arg_list():
            cur_indices = []
            for w in element.split(' '):
                if w in indices_dict:
                    cur_indices.extend(indices_dict[w])
            ret.append(cur_indices)
        return ret

    def only_sentence_words(self):
        """
        Return True iff this oie uses only sentence words.
        """
        all_indices = [ind
                       for elem in self.get_sentence_indices()
                       for ind in elem]

        return len(all_indices) == re.split(' *',
                                            str(self).replace(';', ' '))

    def get_chunks_dic(self):
        """
        Provide the template with all options for chunk names
        and their insansiation + the possibility to reference the asnwer string
        """
        return dict([(chunk_name,
                      str(chunk))
                     for (chunk_name,
                          chunk) in self.qa.chunks.iteritems()] + \
                    [("ANSWER", self.qa.raw_answer_str)])

    def to_see_format(self, see_format):
        """
        Return an "abisee" representation of this extraction
        """
        args = filter(lambda arg: arg != '',
                      [self.instansiate_element(arg).strip()
                       for full_arg in self.get_ordered_arg_list()
                       for arg in full_arg.split(';') # Some slots actually contain multiple args
                      ])

        args_rep = ["(ARG{arg_ind} {arg} )ARG{arg_ind}".\
                    format(arg = self.instansiate_element(arg),
                           arg_ind = arg_ind)
                    for (arg_ind, arg)
                    in enumerate(args)]

        pred_rep = "(PRED {pred} )PRED".format(pred = self.instansiate_element(self.predicate))

        if see_format == "PRED_ONLY":
            ret = [pred_rep]
        else:
            ret = [args_rep[0]] + \
                  [pred_rep] + \
                  args_rep[1:]

        return "(OIE {} )OIE".format(" ".join(ret))


    def instansiate_element(self, elem):
        """
        Plug in place holders in a given template element.
        """
        return elem.format(**self.get_chunks_dic())

    def __str__(self):
        """
        Linearize extraction
        """
        oie_template = self.get_template()
        return self.instansiate_element(oie_template)

if __name__ == "__main__":
    # Parse command line arguments
    args = docopt(__doc__)
    inp_fn = args["--in"]
    out_fn = args["--out"]

    qto = QAtoOIE(inp_fn)
    logging.debug(qto.template_to_oie)

    logging.info("DONE")
