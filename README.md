## Summary

This project features a means to overlay modules and their xml config
atop an existing JBoss AS7 instance. This enables TorqueBox and
Immutant to be merged into a single application server capable of
deploying both Ruby and Clojure applications, for example.

## Usage

overlay.core/-main takes 2 params that should eventually resolve to
two local filesystem paths: a layee and a layer. The latter overlays
the former so the desired result will reside beneath the path to the
layee, i.e. the first param.

It is convenient to download and extract a specific distribution by
simply omitting the second param.

Anything downloaded will be extracted beneath target/.

The specifications of layee and layer may be one of the following:

 - A local path to a jboss, torquebox, or immutant installation
 - A URL to a zipped distro: it'll be fetched and extracted to target/
 - A descriptor in the following form: app[-version]

Currently, only 'immutant' and 'torquebox' are supported as values for
'app', and version defaults to the latest incremental. You can 
specify a specific incremental build number or released version. 
Incremental build numbers are available from 
http://repository-project.forge.cloudbees.com/incremental/ or from
http://torquebox.org/2x/builds/ and http://immutant.org/builds/


So to download the latest torquebox and overlay it with the latest
immutant:

    $ lein overlay torquebox immutant

If you already have two installations of an AS7-based app server, you
can overlay the modules/config of one on the other like so:

    $ lein overlay /path/to/torquebox /path/to/immutant

Alternatively,

    $ lein overlay $TORQUEBOX_HOME $IMMUTANT_HOME

If you know the URL for a zipped archive, you may pass that as the
first parameter:

    $ lein overlay http://repository-torquebox.forge.cloudbees.com/incremental/torquebox/LATEST/torquebox-dist-bin.zip

The output from the run indicates the resulting path of the overlaid
distribution.

## License

Copyright (C) 2011-2013 Jim Crossley
Distributed under the Eclipse Public License, the same as Clojure.
