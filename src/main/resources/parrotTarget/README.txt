Deleting this directory is harmless. The only cost is that it is slower
to deploy if you're developing iteratively.

This directory holds the parrot archives that will be launched by
mesos. We keep them here rather than in tmp because they can be large
enough to be painful to have to recreate when working over a slow
network. rsysnc fixes that, but only if we keep the previous version
around, which is why this directory exists.
