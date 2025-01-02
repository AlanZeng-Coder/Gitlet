package gitlet;

/**
 * Driver class for Gitlet, a subset of the Git version-control system.
 *
 * @author Zien Zeng
 */
public class Main {

    /**
     * Usage: java gitlet.Main ARGS, where ARGS contains
     * <COMMAND> <OPERAND1> <OPERAND2> ...
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Please enter a command.");
            return;
        }
        String firstArg = args[0];
        switch (firstArg) {
            case "":
                System.out.println("Please enter a command");
                return;
            case "init":
                validTest(1, args.length);
                Repository.init();
                break;
            case "add":
                validTest(2, args.length);
                Repository.addFileToStaging(args[1]);
                break;
            case "commit":
                validTest(2, args.length);
                Repository.commit(args[1]);
                break;
            case "restore":
                if (args.length == 3 && args[1].equals("--")) {
                    Repository.restore(args[2]);
                } else if (args.length == 4 && args[2].equals("--")) {
                    Repository.restore(args[1], args[3]);
                } else {
                    System.out.println("Incorrect operands.");
                    return;
                }
                break;
            case "log":
                validTest(1, args.length);
                Repository.log();
                break;
            case "global-log":
                validTest(1, args.length);
                Repository.globalLog();
                break;
            case "rm":
                validTest(2, args.length);
                Repository.rm(args[1]);
                break;
            case "status":
                validTest(1, args.length);
                Repository.status();
                break;
            case "find":
                validTest(2, args.length);
                Repository.find(args[1]);
                break;
            case "branch":
                validTest(2, args.length);
                Repository.createNewBranch(args[1]);
                break;
            case "rm-branch":
                validTest(2, args.length);
                Repository.removeBranch(args[1]);
                break;
            case "switch":
                validTest(2, args.length);
                Repository.switchBranch(args[1]);
                break;
            case "reset":
                validTest(2, args.length);
                Repository.reset(args[1]);
                break;
            case "merge":
                validTest(2, args.length);
                Repository.merge(args[1]);
                break;
            default:
                System.out.println("No command with that name exists.");
                break;
        }
    }

    public static void validTest(int expected, int actual) {
        if (expected != actual) {
            System.out.println("Incorrect operands.");
            return;
        }
    }
}
