print = lessenv.print;
readFile = lessenv.readFile;
arguments = lessenv.arguments;

function error(e, filename) {

    filename = e.filename || filename;

    if (e.stack) {
        warn(filename + ": " + (e.message || "") + "\n" + e.stack);
    } else {
        warn(filename + ":[" + e.line + "," + (e.column + 1) + "] " + (e.message || ""));
        if (showErrorExtracts) {
            if (e.extract[0]) {
                warn("  " + String(parseInt(e.line) - 1) + ":" + e.extract[0]);
            }
            if (e.extract[1]) {
                warn("  " + String(parseInt(e.line)) + ":" + e.extract[1]);
            }
            if (e.extract[2]) {
                warn("  " + String(parseInt(e.line) + 1) + ":" + e.extract[2]);
            }
        }
    }
}

(function (args) {
    var outputFile,
            inputFile,
            outputName,
            inputName,
            haveError = false,
            compress = false,
            i;

    for (i = 0; i < args.length; i++) {
        switch (args[i]) {
            case "-x":
                debug("Compression enabled");
                compress = true;
                break;
            default:
                inputName = args[i];
                inputFile = "/virtual/" + inputName;
                outputName = inputName.substring(0, inputName.lastIndexOf(".")) + ".css";
                outputFile = "/target/" + outputName;
                path = inputFile.split("/");
                path.pop();
                path = path.join("/")

                var input = readFile(inputFile);

                if (!input) {
                    warn(inputName + ': File not found');
                    haveError = true;
                } else {

                    try {
                        debug("Compiling " + inputName + " to " + outputName + " ...");
                        var parser = new less.Parser({paths: [path + "/"]});

                        parser.parse(input, function (e, root) {
                            if (e) {
                                error(e, inputName);
                                haveError = true;
                            } else {
                                var content = root.toCSS({compress: compress || false});
                                writeFile(outputFile, content);
                                debug("Compiled " + inputName + " to " + outputName + ".");
                            }
                        });
                    }
                    catch (e) {
                        error(e, inputName);
                        haveError = true;
                    }
                }

        }
    }

    if (haveError) {
        lessenv.quit(1);
    }
    debug("Finished");
}(arguments));