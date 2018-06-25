""" Usage:
   convert_dropbox --in=INPUT_FILE --sents=SENT_FILE --out=OUTPUT_FILE [--dont_use_alignment] [--extended] [--debug]
"""

import csv
import pdb
import pandas as pd
from pandas import DataFrame
from docopt import docopt
import logging
logging.basicConfig(level = logging.DEBUG)


def align(sent, text, text_mapping):
    """
    Convert text to aligned question in my format
    """
    if not text_mapping:
        logging.debug("empty text: {}".format(text))
        return text

    if "-" in text_mapping:
        # question mapping
        conv = dict([(int(ent.split('-')[0]), int(ent.split('-')[1]))
                     for ent in text_mapping.split(' ')])
    else:
        # answer mapping
        conv = dict([(ans_ind, sent_ind)
                     for (ans_ind, sent_ind)
                     in enumerate(map(int,
                                      sorted(text_mapping.split(' '))))])
    text_tok = text.split(' ')
    sent_tok = sent.split(' ')
    return ' '.join(['{{{{{}|{}}}}}'.format(conv[text_ind], sent_tok[conv[text_ind]])
                     if text_ind in conv else text_tok[text_ind]
                     for text_ind in range(len(text_tok))])

def ignore_answer(answer):
    """
    Should this answer be disregarded?
    """
    return (answer == "<Invalid>") or \
        answer.startswith("<Redundant with")


class Dropbox_parser:
    """
    Manage parsing a dropbox file in
    all different formats
    """

    def __init__(self, sents_dict, use_alignment, extended):
        """
        Init internal state
        use_alignment - whether to use the alignemnt provided in the file
        extended - whether to expect extended or standard format in the file
        """
        self.df = DataFrame()
        self.cur_df_index = 0
        self.use_alignment = use_alignment
        self.parse_row = self.parse_extended_row if extended \
                         else self.parse_standard_row
        self.sents_dict = sents_dict

    def parse_standard_row(self, row):
        """
        Parse a standard row into df
        """
        ptb_ind,sent_ind, \
            worker_id, special_word, question_mapping, question = row[: 6]

        num_of_annots = NUM_OF_ANNOTS
        answers = [ans for ans in row[6 : 6 + num_of_annots]]
        answers_mapping = [m for m in row[6 + num_of_annots : 6 + (2 * num_of_annots)]]

        for answer, ans_map in zip(answers, answers_mapping):
            if not ignore_answer(answer):
                output_row = [worker_id,
                              special_word,
                              question,
                              answer] +\
                              ([align(sent, question, question_mapping), align(sent, answer, ans_map)]
                                 if self.use_alignment else []) # Potentially drop the mapping
                self.df[self.cur_df_index] = output_row
                self.cur_df_index += 1

    def parse_extended_row(self, row):
        """
        Parse a row into df
        """
        # ptb_ind,sent_ind, (special_words_group),\
            #     worker_id, special_word, question_mapping, question = row[: 7]

        sent_id, special_words_group, worker_id, qa_index, \
            special_word_ind, question, ans_map, validator_1, validator_2 = row
        sent = self.sents_dict[sent_id].split(' ')
        answers = [" ".join([sent[int(word_ind)]
                             for word_ind in ls])
                   for ls in [ans_map.split(),
                              validator_1.split(':')[1].split(),
                              validator_2.split(':')[1].split()]]
        special_word= sent[int(special_word_ind)]

        # answers = [ans for ans in row[8 : 8 + num_of_annots]]
        # answers_mapping = [m for m in row[8 + num_of_annots : 8 + (2 * num_of_annots)]]
        for answer in answers:
            output_row = [worker_id,
                          special_word,
                          question,
                          answer] # +\
                          # ([align(sent, question, question_mapping), align(sent, answer, ans_map)]
                          #  if self.use_alignment else []) # Potentially drop the mapping
            self.df[self.cur_df_index] = output_row
            self.cur_df_index += 1


# Constants
NUM_OF_ANNOTS = 3

if __name__ == "__main__":
    args = docopt(__doc__)
    logging.debug(args)
    fn_in = args["--in"]
    sent_fn = args["--sents"]
    fn_out = args["--out"]
    use_alignment = not args["--dont_use_alignment"]
    extended = bool(args["--extended"])
    cnt = 0
    sent_ind = None
    sents_dict = dict([line.strip().split('\t')
                       for line in open(sent_fn)])
    with open(fn_in) as fin:
        reader = csv.reader(fin, delimiter = '\t', quotechar='|')
        db = Dropbox_parser(sents_dict, use_alignment, extended) # Init dropbox parser
        for row in reader:
            cur_sent_ind = row[0]
            if (cur_sent_ind != sent_ind):
                # This is a row introducing a new sentence
                sent_ind = cur_sent_ind
                sent = sents_dict[cur_sent_ind]
                db.df[db.cur_df_index] = ([sent] + [''] * (5 if use_alignment else 3))
                db.cur_df_index += 1

            db.parse_row(row)

    # Get the parsed dataframe from the dropbox instance
    df = db.df
    df.transpose().to_csv(fn_out, header = False, index = False, line_terminator='\n')
    logging.info("DONE")
