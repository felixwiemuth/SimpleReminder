cp LICENSE.md OPEN_SOURCE_LICENSES.md
git apply OPEN_SOURCE_LICENSES.md.patch
pandoc OPEN_SOURCE_LICENSES.md -f markdown -t html -s -o app/src/main/assets/open_source_licenses.html
rm OPEN_SOURCE_LICENSES.md
cp -r LICENSES/ app/src/main/assets/
