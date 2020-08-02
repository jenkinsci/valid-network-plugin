package io.jenkins.plugins;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.Proc;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import hudson.Extension;
import hudson.tasks.BuildStepDescriptor;
import jenkins.tasks.SimpleBuildStep;
import hudson.util.ArgumentListBuilder;
import hudson.util.Secret;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

public class CliExecutor extends Builder implements SimpleBuildStep {
	private final String username;
	private final String filepath;
	private final String projectId;
	private final Secret password;
	private Launcher launcher;
	
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
		this.filepath = filepath;
		this.projectId = projectId;
		this.password = Secret.fromString(password);
	}

	private boolean isWin() {
		return hudson.Functions.isWindows();
	}

	private void print(String msg) {
		this.listener.getLogger().println(msg);
	}

	private void cliExecute(String ...cmd) throws IOException, InterruptedException {
		String cwd = System.getProperty("user.dir");
		String cliExecPath = this.isWin() ? 
				cwd + "\\src\\main\\resources\\binaries\\" + this.cliExec : 
				cwd + "/src/main/resources/binaries/" + this.cliExec;
		
		ProcStarter ps = this.launcher.launch();
		
		List<String> cmdList = new ArrayList<String>();
		cmdList.add(cliExecPath);
		for (String str: cmd) {
			cmdList.add(str);
		}
		
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(cmdList);
		
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		
		Proc proc = launcher.launch(ps.cmds(args).stdout(out).stderr(err));
		
		int exitCode = proc.join();
		if (exitCode == 0) {
			print(out.toString());
		}
		else {
			throw new IOException(err.toString());
		}
	}

	private void configHost() throws IOException, InterruptedException {
		print("Setting up config host");
		this.cliExecute("config", "--host", "console.valid.network");
	}

	private void login() throws IOException, InterruptedException {
		print("Logging in...");
		this.cliExecute("login", "-u", this.username, "-p", Secret.toString(this.password));
	}
	
	private void createDraft() throws IOException, InterruptedException {
		print("Creating draft");
		this.cliExecute("projectsRegistry", "projects", "projectId", "newDraft", "set", "--projectId", this.projectId);
	}
	
	private void addFile() throws IOException, InterruptedException {
		print("Adding file " + this.filepath);
		this.cliExecute("drafts", "projects", "files", "set", "--projectId", this.projectId, "--filepath", "\"" + this.filepath + "\"");
	}
	
	private void finishUpload() throws IOException, InterruptedException {
		print("Finishing upload");
		this.cliExecute("drafts", "projects", "get", "--projectId", this.projectId);
		this.cliExecute("drafts", "projects", "set", "--projectId", this.projectId,  "--commit", "--commitAmount", "1", "--compilerVersion", "0.4.25", "--keepHistory");
		this.cliExecute("projectsRegistry", "projects", "projectId", "details", "get", "--projectId", this.projectId);
		print("Completed file upload as new package");
	}

	@Override
	public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
			throws InterruptedException, IOException {

		this.launcher = launcher;
		this.listener = listener;
		this.cliExec = this.isWin() ? "valid-network-cli-tool.exe" : "valid-network-cli-tool";
		this.configHost();
		this.login();
		this.createDraft();
		this.addFile();
		this.finishUpload();
	}

	@Symbol("validNetwork")
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