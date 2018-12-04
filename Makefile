all: install

install: compile shippable
	cp -rf appmagyar/* shippable

shippable:
	mkdir -p shippable

compile: zentaworkaround appmagyar.compiled codedocs

codedocs: shippable/appmagyar-testcases.xml 

shippable/appmagyar-testcases.xml: appmagyar.richescape shippable
	zenta-xslt-runner -xsl:generate_test_cases.xslt -s appmagyar.richescape outputbase=shippable/appmagyar-

inputs/appmagyar.issues.xml:
	mkdir -p inputs
	touch inputs/appmagyar.issues.xml:

include /usr/share/zenta-tools/model.rules

clean:
	git clean -fdx
	rm -rf zenta-tools

zentaworkaround:
	mkdir -p ~/.zenta/.metadata/.plugins/org.eclipse.e4.workbench/
	cp workbench.xmi ~/.zenta/.metadata/.plugins/org.eclipse.e4.workbench/
	touch zentaworkaround

testenv:
	docker run --rm -p 5900:5900 -e PULL_REQUEST=false -e ORG_NAME=local -v $$(pwd):/appmagyar -w /appmagyar -it edemo/pdengine

