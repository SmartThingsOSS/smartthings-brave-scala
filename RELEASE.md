# Release Process
---
This repo uses semantic versions. Please keep this in mind when choosing version numbers.

1. Push a git tag
   
   The tag should be of the format `vN.M.L`, for example `v1.0.1`.
   
1. Wait for CircleCI

   This is managed via a series of run commands that invoke SBT tasks.  Publication of artifacts is completed by the 
   SBT publish and artifacts are released in Bintray with the SBT Bintray plugin.
   