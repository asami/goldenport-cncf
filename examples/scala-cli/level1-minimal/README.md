Level 1 Demo — Using goldenport-cncf.jar inside Docker container
===============================================================

Assumptions
-----------
- You are in the CNCF repository
- Docker image `goldenport-cncf` is already built
- Demo component source exists at:
  examples/scala-cli/level1-minimal/DemoComponent.scala
- Docker image contains:
  /app/goldenport-cncf.jar

Step 1: Move to Level 1 demo directory
--------------------------------------
cd examples/scala-cli/level1-minimal

Step 2: Run scala-cli inside Docker, using the container's CNCF runtime
-----------------------------------------------------------------------
docker run --rm \
  -v $(pwd):/work \
  -w /work \
  goldenport-cncf \
  scala-cli run DemoComponent.scala \
    --classpath /app/goldenport-cncf.jar \
    -- \
    command demo ping

Expected output
---------------
ok

Result
------
- The demo component is compiled by scala-cli
- The CNCF runtime is the goldenport-cncf.jar inside the Docker container
- The component is loaded via classpath
- The `ping` operation returns result_success("ok")

Notes
-----
- No sbt project is required
- No CNCF source code is needed on the host
- This demonstrates "component development outside the runtime image"
- component.dir is intentionally NOT used at this stage
