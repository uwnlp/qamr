# QAMR Code

There are three main subprojects:

 * `qamr`: The QAMR annotation pipeline, instantiable for any data.
 * `qamr-example`: Example instantiation of the pipeline used to generate the data in this repository.
 * `qamr-analysis`: Code for processing the data, including performing our manual analysis from the paper.

## Setup

Prerequisites:

 * [sbt](http://www.scala-sbt.org/) to run the project, and
 * [wget](https://www.gnu.org/software/wget/) to download some dependencies (if necessary).

After installing the prerequistes, clone this repository and run `scripts/setup.sh`.
This will load two necessary dependencies:

  * [spacro](https://github.com/julianmichael/spacro), used to run the MTurk annotation pipeline, and
  * [nlpdata](https://github.com/julianmichael/nlpdata), used for text processing and NLP datasets.

(They're loaded as submodules and published locally because I haven't bothered to publish them on Maven.)
The setup script will then prompt you to download several datasets to support the annotation pipeline.
These are not necessary for the QAMR annotation pipeline (the `qamr` project) if you're running it with your own data,
but they are necessary for our pipeline (`qamr-example`) or the data analysis (coming soon).
In the latter cases you will also have to download the Penn Treebank (version 2) and place it at `datasets/ptb`.

## Annotation pipeline

See the code in `qamr-example` to get an idea of how you would set up an annotation pipeline for your own data.
Here we will write instructions for running that pipeline, but they translate to any similar project.

#### Local dry run

To make sure everything is working, you should first start the webserver and try local previews of the task interface.
To do so, follow these instructions:

 1. Download the Penn Treebank and Wiki1k datasets (the latter you can get with `scripts/setup.sh`).
 2. Make sure you have an [MTurk requester account](https://requester.mturk.com/) associated with an [Amazon AWS](https://aws.amazon.com/) account.
 3. Make that your AWS credentials are at `~/.aws/credentials`.
 4. Run `scripts/run_example.sh`.
    This will launch an SBT console that you can use to manage the webserver and Turk tasks.
 5. After the console has finished loading, type `init` to start the webserver.
 6. In a separate terminal, run `tail -f example.log`.
    You should see a message like `Server is listening on http://0:0:0:0:0:0:0:0:8888`.
    There will also be an `HTTPS configuration failed` message; this is expected.
 7. In a browser window, open `http://localhost:8888/task/generation/preview`.
    This should show you the instructions and a sample interface for the question-writing task.
    (Submitting results will not work on this preview, but you can try out the interface.)
    Similarly for the question-answering task at `http://localhost:8888/task/validation/preview`.
    
If you wish to shut down the server and end the session, type `exit` to stop the processes from running and then `:q` to exit the console.
(If you don't type `exit` first, the process will hang waiting for the actor system and logging to terminate, which it won't.)

#### Running on the MTurk Sandbox

Once you've verified that the server and interfaces are working, you can try running on the MTurk Sandbox.
Follow these instructions to run our example pipeline:

 1. Get access to a public-facing webserver with a domain name (denoted `<domain>`) and a globally trusted SSL certificate to host HTTPS.
 2. Make sure all of the following is done on a machine that can host a webserver at that domain.
 3. Use the [`openssl`](https://www.openssl.org/docs/man1.0.2/apps/openssl.html) command line tool to generate a keystore for your certificate in the `.p12` format.
 4. Place the keystore at `qamr-example/jvm/src/main/resources/<domain>.p12`.
 5. Place the keystore password in a standalone file at `qamr-example/jvm/src/main/resources/<domain>-keystore-password`.
 6. In `scripts/init_example.scala`, change `val domain` from `localhost` to `<domain>`.
 7. Follow the dry run instructions. This time, after running `init`, in the logs you should see
    something like `Server is listening on https://0:0:0:0:0:0:0:0:8080` (note the HTTPS scheme in the URL).
 8. On the SBT console, run `exp.start()`. This will upload a few HITs to the sandbox.
    You should see the URLs to access these HITs appear in the logs.
    This will also cause the system to start polling MTurk for finished results.
    The polling interval can be changed by running e.g. `exp.stop(); exp.start(10 seconds)`,
    and you can poll manually with `exp.update`.
 9. Access your HITs on the sandbox by visiting the URLs printed in the logs.
    (There will only be two distinct URLs; one for the generation task and another for the validation task.
    The validation HITs will only start being uploaded once generation results are processed.)
    The first time you do the HITs, you will have to request the qualifications or complete the qualification test.
    Try submitting some results and verify in the logs that they are being processed on the server side and new HITs are being uploaded.
 10. Try running `exp.allGenInfos` and `exp.allValInfos`.
     These will return lists of all of the results of crowdsourcing so far.
 11. Open a browser window to `http://<domain>:8888/task/dashboard/preview`.
     This will give you a (live updating) dashboard of worker stats, recent feedback, and recent results.
     Check this dashboard for an at-a-glance idea of how things are going,
     as well as recent results for full sentences.
 12. If you wish to stop the annotation partway through, run `deleteAll`.
     This will stop the system from uploading new HITs, expire all existing HITs for the tasks that are running, then remove them from MTurk.
     After running this, watch the logs and wait until the system finishes approving, expiring, and deleting HITs.
     Then verify that the result of `getActiveHITIds` is empty, meaning all HITs have been deleted.
     In production, you may need to run `deleteAll` several times since a HIT cannot be expired or deleted while a worker is working on it.
     Once `getActiveHITIds` returns an empty result, you're ready to run whatever you want on the console to analyze the results,
     or to `exp.stop(); exit`.

For a full list of functions available to you at the SBT console to manage the task and Turk,
check the classes `qamr.AnnotationPipeline` and `qamr.example.AnnotationSetup`, as well as the convenience functions
defined in `scripts/init_example.scala`.
Also note that you can make calls to the MTurk service directly at `config.service`,
and in general execute arbitrary Scala code.

#### Running on MTurk in production

 1. In `scripts/init_example.scala`, set `isProduction` to `true`.
 2. Make sure you have enough funds in your MTurk account.
 3. Follow the steps given for running in the sandbox.
 4. When annotation is finished, to output the results in the official format
    to `data/example/static/out`, run `data.writeAllTSVs`.
 
### Advanced usage

If you want to safely do more advanced things (e.g., make a change to the interface and swap it out without taking all of the HITs down first),
you'll have to be careful because of various issues, for example changes in the HIT type that might result from changes in your code,
which would affect how the results are stored and retrieved on disk.
To understand these issues, I encourage you to take a closer look at the code in this repository or
in the [spacro](https://github.com/julianmichael/spacro) commit being loaded as a submodule.
If you have any questions, or if anything doesn't work as expected, please file an issue and I'll be happy to help.

## Analysis

To run the manual analysis to produce the numbers in the NAACL paper, run `scripts/run_analysis.sh`.
You can also produce a SQuAD-formatted version of the dataset with `scripts/write_squad_formatted.sh`.
The `.json` files will be in `data/example/static/out/`.

See the `manual-analysis/` directory for the 150-question sample we annotated for the manual analysis,
including a description of the coding scheme.

Numbers output by our analysis code are very close to what is published in the paper, but are very
slightly off due to unimportant differences in the details of how they were calculated (i.e.,
"reproduction noise").
