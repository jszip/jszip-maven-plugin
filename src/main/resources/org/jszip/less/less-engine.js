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

function engine(inputName, encoding, compress) {
    path = inputName.split("/");
    path.pop();
    path = path.join("/")

    var input = readFile(inputName, encoding);

    if (!input) {
        throw new Error(inputName + ': File not found');
    } else {
        debug("Compiling " + inputName + " ...");
        var parser = new less.Parser({paths: [path + "/"]});
        var result = {};

        parser.parse(input, function (e, root) {
            if (e) {
                result.error = e;
            } else {
                result.css = root.toCSS({compress: compress || false});
            }
        });
        if (result.error) {
            var e = new Error(result.error.message || "");
            e.line = result.error.line;
            e.col = result.error.col+1;
            throw e;
        }
        if (!result.css) {
            throw new Error("Could not parse included file");
        }
        return result.css;
    }
};