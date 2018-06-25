""" Usage:
    align_exp --in=INPUT_FILE --out=OUTPUT_FILE

    Align QA to sentences a given experiment file
    outputs the aligned version into out_fn
"""

from docopt import docopt
from preproc import Aligner
import logging
logging.basicConfig(level = logging.DEBUG)

if __name__ == "__main__":
    args = docopt(__doc__)
    inp = args["--in"]
    out = args["--out"]
    logging.info("Aligning experiment file {} into {} ...".format(inp, out))
    aligner = Aligner()
    aligner.align_experiment(inp, out)
    logging.info("DONE!")
