#!/bin/bash

set -e

start=$(date +%s)

if [ -z "${JCSP_DIR}" ]
then
  echo "JCSP_DIR variable is required"
  exit 1
fi

if [ -z "${PROJECT_DIR}" ]
then
  PROJECT_DIR=$(pwd)
fi

TARGET="$PROJECT_DIR/safe-ec-tests"
rm -rf "$TARGET"

if [ -z "${MAVEN}" ]
then
  MAVEN=mvn
fi

function compileModule() {
  local NAME=$1
  shift 1
  echo "Compiling $NAME..."
  local MODULE="$PROJECT_DIR/$NAME"

  local MODULE_TARGETS=("$@")
  if [ ${#MODULE_TARGETS[@]} -eq 0 ]; then
      MODULE_TARGETS=("$NAME/target")
  fi

  pushd "$MODULE" || exit 1
  $MAVEN clean install -U "-Djcspdir=$JCSP_DIR"
  mkdir -p "$TARGET"

  for MODULE_TARGET in "${MODULE_TARGETS[@]}"; do
    find "$PROJECT_DIR/$MODULE_TARGET" -type f -name "*.jar" -exec cp {} "$TARGET" \;
  done

  echo "$NAME compiled"
  popd || exit 1
}

rm -rf "$PROJECT_DIR/target"

echo "Installing JML to local maven repo"
"$PROJECT_DIR/analyzer-back/plugins/safecurves-validator/install-jml.sh" 1.3.1
echo "Java Math Library by Tillman-Neumann installed"

compileModule "" "analyzer-back/safe-ec-server/target" "analyzer-back/plugin-api/target" "analyzer-front/safe-ec-java/target"
rm "$TARGET/safe-ec-java-1.0.jar"
rm "$TARGET/safe-ec-server-1.0.jar"

compileModule "analyzer-back/plugins/safecurves-validator"
mkdir "$TARGET/plugins" && find "$TARGET" -type f -name "safecurves-validator*.jar" -exec mv {} "$TARGET/plugins" \;

compileModule "test/swift-connector"

cp "test/safe-ec.yml" "$TARGET"
mkdir "$TARGET/lib"
$MAVEN dependency:copy -Dartifact=de.tillman_neumann:java-math-library:1.3.1 "-DoutputDirectory=$TARGET/lib"
$MAVEN dependency:copy -Dartifact=io.churchkey:churchkey:1.22 "-DoutputDirectory=$TARGET/lib"

echo "Archiving result..."
TAR_NAME=safe-ec-for-tests.tar.gz
rm -rf "$TAR_NAME"
pushd "$PROJECT_DIR" || exit 1
tar -czvf "$PROJECT_DIR/$TAR_NAME" "safe-ec-tests"
popd || exit 1
echo "SafeEC bundle for tests is available at $(pwd)/$TAR_NAME"

end=$(date +%s)
echo "Elapsed time: $((end-start)) seconds"
