=====================================
Welcome to OptaWeb Employee Rostering
=====================================

Quick start
-----------

If you want to run the application without building it from source, go to `bin`
directory and use the run script. You only need Java Runtime Environment (JRE).

Documentation
-------------

Read the complete documentation under `reference_manual` directory in HTML
or PDF format.

Build from source
-----------------

The distribution contains complete project sources under the `sources`
directory. You can build the project with Maven. If you want to start hacking
on the distributed sources, it might be a good idea to initialize a Git
repository first so that you can keep track of your changes. You can do that
with:

  cd sources/
  git init && git add . && git commit -m 'Initial commit'

Find out more in `sources/README.adoc` or in the documentation.

Run on OpenShift
----------------

Build the project first. Then deploy it to OpenShift using the
`runOnOpenShift.sh` script under `sources` directory.
