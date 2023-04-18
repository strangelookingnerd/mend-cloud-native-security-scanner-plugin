package io.jenkins.plugins.sample;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.Secret;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MendCnScannerBuilder extends Builder implements SimpleBuildStep {

    private final String userEmail;
    private final Secret userKey;
    private final String mendUrl;
    private final String repoNames;

    @DataBoundConstructor
    public MendCnScannerBuilder(String userEmail, Secret userKey , String mendUrl, String repoNames) {
        this.userEmail = userEmail;
        this.userKey = userKey;
        this.mendUrl = mendUrl;
        this.repoNames = repoNames;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public Secret getUserKey() {
        return userKey;
    }

    public String getMendUrl() {
        return mendUrl;
    }

    public String getRepoNames() {
        return repoNames;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, EnvVars env, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {

        listener.getLogger().println("<<< Mend Cloud Native Security Scanner >>>");
        // set variables for mend login
        setScannerEnvVariables(env);

        // download CLI
        downloadScanner(listener.getLogger());

        for (String repoName : repoNames.split(",")) {
            listener.getLogger().println("Processing repository: " + repoName);
            // find recent image to scan
            String tag = extractImageTag(launcher, env, listener, run.getRootDir(), repoName);
            if (tag != null) {
                // run CLI scan
                String scanTag = repoName + ":" + tag;
                listener.getLogger().println("Performing Mend image scan for tag: " + scanTag);
                executeCommand(launcher, env, listener, null, "mend", "image", scanTag, "--no-color");
            }
        }
    }

    private void setScannerEnvVariables(EnvVars env) {
        env.put("MEND_EMAIL", userEmail);
        env.put("MEND_USER_KEY", userKey.getPlainText());
        env.put("MEND_URL", mendUrl);
    }

    private void downloadScanner(PrintStream logger) throws IOException {

        String scannerDownloadUrl = createDownloadUrl();

        logger.println("Downloading CLI... " + scannerDownloadUrl);
        File scannerFile = new File(FilenameUtils.getName(scannerDownloadUrl));
        // grant executable permission
        scannerFile.setExecutable(true);
        FileUtils.copyURLToFile(new URL(scannerDownloadUrl), scannerFile);
    }

    private String extractImageTag(Launcher launcher, EnvVars env, TaskListener listener, File dir, String repoName) throws IOException, InterruptedException {
        PrintStream logger = listener.getLogger();
        File tempOutputFile = new File(dir, "tempOutputFile");
        try {
            //docker images display tags by creation date
            logger.println("Extracting latest image tag...");
            executeCommand(launcher, env, listener, tempOutputFile, "docker", "images", repoName, "--format={{.Tag}}");
            String text = FileUtils.readFileToString(tempOutputFile, StandardCharsets.UTF_8);
            if (!text.isEmpty()) {
                String[] repoTags = text.split("\n");
                if (repoTags.length == 0) {
                    logger.println("No installed tags were found");
                } else return repoTags[0];
            }
        }
        finally {
            tempOutputFile.deleteOnExit();
        }
        return null;
    }

    private void executeCommand(Launcher launcher, EnvVars env, TaskListener listener, File tempOutputFile, String... args) throws IOException, InterruptedException {
        Launcher.ProcStarter ps = launcher.launch();

        if (tempOutputFile != null) {
            PrintStream printStream = new PrintStream(tempOutputFile, StandardCharsets.UTF_8);
            ps.stdout(printStream);
            ps.stderr(printStream);
        }
        else {
            ps.stdout(listener);
            ps.stderr(listener.getLogger());
        }

        ps.envs(env);
        ps.cmds(args);
        ps.stdin(null);
        ps.quiet(true);

        ps.join();
    }

    private String createDownloadUrl() {
        String scannerFileName = "mend";
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("windows")) {
            os = "windows";
            scannerFileName = "mend.exe";
        }
        if (os.contains("mac")) {
            os = "darwin";
        }
        if (os.contains("linux")) {
            os = "linux";
        }

        String arch = System.getProperty("os.arch").toLowerCase();

        return  "https://downloads.mend.io/cli/" + os + "_" + arch + "/" + scannerFileName;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Mend Cloud Native Security Scanner";
        }

    }

}
