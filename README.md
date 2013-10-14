## Summary

This project features a means to overlay modules and their xml config
atop an existing JBoss AS7 instance. This enables TorqueBox and
Immutant to be merged into a single application server capable of
deploying both Ruby and Clojure applications, for example.

## Usage

Clone the overlay repository, cd into the project, and run this:

    $ lein overlay <target> <source>

The `target` represents the "layee", i.e. the thing to be overlaid.
The `source` is the "layer". The source modules and config will
overlay the target modules and config. The desired result will reside
beneath the path to the target, i.e. the first parameter.

The specifications of layee and layer may be one of the following:

 - A local path to a jboss, torquebox, or immutant installation
 - A URL to a zipped distro: it'll be fetched and extracted to target/
 - A descriptor in the following form: app[-version]

Version defaults to the latest incremental. You can specify a specific
incremental build number or released version.

It is convenient to download and extract a specific distribution by
simply omitting the second param.

Anything downloaded will be extracted beneath target/.

So to download the latest torquebox and overlay it with the latest
immutant:

    $ lein overlay torquebox immutant

If you already have two installations of an AS7-based app server, you
can overlay the modules/config of one on the other like so:

    $ lein overlay /path/to/eap-6.1 ~/.immutant/current

Alternatively,

    $ lein overlay $TORQUEBOX_HOME $IMMUTANT_HOME

If you know the URL for a zipped archive, you may pass that as the
first parameter:

    $ lein overlay http://repository-torquebox.forge.cloudbees.com/incremental/torquebox/LATEST/torquebox-dist-bin.zip

The output from the run indicates the resulting path of the overlaid
distribution.

By default, files in common to both layer and layee beneath `modules/`
will *not* be overwritten, unless you specify the `--overwrite`
option (or `-o`).

    $ lein overlay --overwrite layee layer
