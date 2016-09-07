all: clean compile copy jar

clean: 
	rm -f NGSEPcore_3.0.2.jar
	rm -rf bin
	
compile:
	mkdir bin 
	javac -cp lib/jsci-core.jar:lib/sam-1.68.jar -d bin src/ngsep/*.java src/ngsep/*/*.java src/ngsep/*/*/*.java

copy: 
	cp -f src/ngsep/transcriptome/ProteinTranslatorDefaultBundle.properties bin/ngsep/transcriptome/

jar: 
	mkdir dist
	jar -xf lib/jsci-core.jar JSci
	mv JSci dist/
	jar -xf lib/htsjdk.jar htsjdk
	mv htsjdk dist/
	cp -r bin/* dist/
	jar -cfe NGSEPcore_3.0.2.jar ngsep.NGSEPcore -C dist . 
	rm -rf dist