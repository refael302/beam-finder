# GitHub distribution signing

`github-distribution.keystore` signs **github** flavor APKs (local + CI) so in-app updates
from GitHub Releases keep a stable signature.

This key is for sideload continuity only, not Play Store. Play builds use their own signing.

Apps already installed with a different debug key must uninstall once, then install a
release signed with this keystore; later OTA updates will work.
