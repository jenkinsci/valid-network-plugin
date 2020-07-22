package io.jenkins.plugins;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import hudson.Extension;
import hudson.tasks.BuildStepDescriptor;
import jenkins.tasks.SimpleBuildStep;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

public class CliExecutor extends Builder implements SimpleBuildStep {
	private final String username;
	private final String password;
	private final String filepath;
	private final String projectId;

	private String cliExec;
	private TaskListener listener;

	public String getProjectId() {
		return this.projectId;			
	}
	
	public String getFilepath() {
		return this.filepath;
	}
	
	@DataBoundConstructor
	public CliExecutor(String username, String password, String projectId, String filepath) {
		this.username = username;
		this.password = password;
		this.filepath = filepath;
		this.projectId = projectId;
	}

	private boolean isWin() {
		return System.getProperty("os.name").toLowerCase().startsWith("windows");
	}

	private void print(String msg) {
		this.listener.getLogger().println(msg);
	}

	private void cliExecute(String cmd) throws IOException {
		String cwd = System.getProperty("user.dir");
		String execCmd = this.isWin() ? 
				cwd + "\\src\\main\\resources\\binaries\\" + this.cliExec + " " + cmd : 
				cwd + "/src/main/resources/binaries/" + this.cliExec + " " + cmd;
		
		Process proc = Runtime.getRuntime().exec(execCmd);

		BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
		BufferedReader stdError = new BufferedReader(new InputStreamReader(proc.getErrorStream()));

		String stdout = "";
		String stderr = "";

		String s = null;
		while ((s = stdInput.readLine()) != null) {
			stdout += s;
		}

		while ((s = stdError.readLine()) != null) {
			stderr += s;
		}

		if (!stderr.equals("")) {
			throw new IOException(stderr);
		} else {
			print(stdout);
		}
	}

	private void configHost() throws IOException {
		print("Setting up config host");
		this.cliExecute("config --host console.valid.network");
	}

	private void login() throws IOException {
		print("Logging in...");
		this.cliExecute("login -u " + this.username + " -p " + this.password);
	}
	
	private void createDraft() throws IOException {
		print("Creating draft");
		this.cliExecute("projectsRegistry projects projectId newDraft set --projectId " + this.projectId);
	}
	
	private void addFile() throws IOException {
		print("Adding file " + this.filepath);
		this.cliExecute("drafts projects files set --projectId " + this.projectId + " --filepath " + "\"" + this.filepath + "\"");
	}
	
	private void finishUpload() throws IOException {
		print("Finishing upload");
		this.cliExecute("drafts projects get --projectId " + this.projectId);
		this.cliExecute("drafts projects set --projectId " + this.projectId + " --commit --commitAmount 1 --compilerVersion 0.4.25 --keepHistory");
		this.cliExecute("projectsRegistry projects projectId details get --projectId " + this.projectId);
		print("Completed file upload as new package");
	}

	@Override
	public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
			throws InterruptedException, IOException {

		this.listener = listener;
		this.cliExec = this.isWin() ? "valid-network-cli-tool.exe" : "valid-network-cli-tool";
		this.configHost();
		this.login();
		this.createDraft();
		this.addFile();
		this.finishUpload();
	}

	@Symbol("greet")
	@Extension
	public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

		@Override
		public String getDisplayName() {
			return "Valid Network Blockhain Security";
		}

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}
	}
}