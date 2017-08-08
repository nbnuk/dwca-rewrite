import static groovy.io.FileType.FILES
import java.util.zip.*
import java.nio.file.Files
import groovy.xml.*

public class DwCARewrite {
    public static void main(String[] args) {

        if(args.length != 2){
            println ("Supply a source and an output directory.")
            return
        }

        if(!new File(args[1]).exists()){
            println("Output directory does not exist " + args[1])
            return
        }

        new File(args[0]).eachFileRecurse(FILES) {
            if(it.name.endsWith('.zip')) {
                try {
                    def archiveName = it.name.replaceFirst(~/\.[^\.]+$/, '')
                    // archive path is the directory structure to the zip file
                    def archivePath = it.getPath() - args[0] - it.name
                    print(new Date(System.currentTimeMillis()).toString())
                    print(" " + it.getPath() - args[0])

                    // temporary folder in which the updates are done
                    def tempDir = args[0] + "/temp/" + archiveName

                    // unzip the archive
                    def zip = new ZipFile(it)
                    zip.entries().each { fSrc ->
                        if (!fSrc.isDirectory()) {
                            def fOut = new File(tempDir + File.separator + fSrc.name)

                            //create temmporary folder
                            new File(fOut.parent).mkdirs()

                            def fos = new FileOutputStream(fOut)
                            def fis = zip.getInputStream(fSrc)

                            fos.write(fis.getBytes())
                            fos.close()
                        }
                    }
                    print(" archive unzipped")
                    zip.close()

                    // get the metadata file
                    def metaFile = new File(tempDir + File.separator + "meta.xml")
                    def archive = new XmlSlurper(false, false).parseText(metaFile.text)
                    assert archive.core.field.find { it.@index == '0' }
                            .@term == "http://rs.tdwg.org/dwc/terms/occurrenceID", "Incorrect term for field 0"
                    assert archive.core.field.find { it.@index == '1' }
                            .@term == "http://rs.tdwg.org/dwc/terms/recordNumber", "Incorrect term for field 1"

                    // make the changes to the data file
                    archive.core.field.find { it.@index == '0' }
                            .@term = "http://rs.tdwg.org/dwc/terms/recordNumber"

                    archive.core.field.find { it.@index == '1' }
                            .@term = "http://rs.tdwg.org/dwc/terms/occurrenceID"

                    metaFile.withWriter { outWriter ->
                        XmlUtil.serialize(new StreamingMarkupBuilder().bind { mkp.yield archive }, outWriter)
                    }
                    print(", updated")
                    // zip it all back up again
                    def zipFileName = args[1] + archivePath + archiveName + "_updated.zip"

                    // keep the directory structure the same
                    new File(args[1] + archivePath).mkdirs()

                    ZipOutputStream output = new ZipOutputStream(new FileOutputStream(zipFileName))
                    new File(tempDir).eachFile() { file ->

                        output.putNextEntry(new ZipEntry(file.name.toString()))

                        InputStream input = new FileInputStream(file);

                        // stream data to the zip file
                        Files.copy(input, output)
                        output.closeEntry();
                        input.close()
                    }
                    output.close();
                    print(", re-zipped")
                    // delete the temp folder
                    new File(tempDir).deleteDir()
                    println(" and temporary files deleted")
                }
                catch(AssertionError e) {
                    println("")
                    println("Error: " + e.toString())
                }
                catch(Exception ex) {
                    println("")
                    println("Error: " + ex.toString())
                }
            }
        }

    }
}
