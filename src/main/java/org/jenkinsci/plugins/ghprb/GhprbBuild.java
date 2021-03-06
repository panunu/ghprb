package org.jenkinsci.plugins.ghprb;

import hudson.model.AbstractBuild;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.github.GHCommitState;

/**
 *
 * @author jbrazdil
 */
public class GhprbBuild {
	private final QueueTaskFuture<?> future;
	private final GhprbRepo repo;
	private final int pull;
	private final boolean merge;
	private AbstractBuild<?, ?> build;
	private boolean finished;

	GhprbBuild(GhprbRepo repo, int pull, QueueTaskFuture<?> future, boolean merge) {
		this.repo = repo;
		this.pull = pull;
		this.future = future;
		this.build = null;
		this.merge = merge;
		this.finished = false;
	}

	public void check() {
		if (build == null && future.getStartCondition().isDone()) {
			try {
				build = (AbstractBuild<?, ?>) future.getStartCondition().get();
			} catch (Exception ex) {
				Logger.getLogger(GhprbBuild.class.getName()).log(Level.SEVERE, null, ex);
			}
			onStarted();
		} else if (build != null && future.isDone()) {
			finished = true;
			onFinished();
		}
	}

	public boolean isFinished(){
		return finished;
	}

	public boolean cancel() {
		if (build == null) {
			try {
				build = (AbstractBuild<?, ?>) future.waitForStart();
			} catch (Exception ex) {
				Logger.getLogger(GhprbBuild.class.getName()).log(Level.WARNING, null, ex);
				return false;
			}
		}
		if (build.getExecutor() == null) {
			return false;
		}

		build.getExecutor().interrupt();
		finished = true;
		return true;
	}

	@Override
	public int hashCode() {
		int hash = 3;
		hash = 71 * hash + this.pull;
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final GhprbBuild other = (GhprbBuild) obj;
		return this.pull == other.pull;
	}

	public int getPullID() {
		return pull;
	}

	public boolean isMerge() {
		return merge;
	}

	protected void onStarted() {
		try {
			build.setDescription("<a href=\"" + repo.getRepoUrl()+"/pull/"+pull+"\">Pull request #"+pull+"</a>");
		} catch (IOException ex) {
			Logger.getLogger(GhprbBuild.class.getName()).log(Level.SEVERE, "Can't update build description", ex);
		}
	}

	protected void onFinished() {
		GHCommitState state;
        String message = getSuccessMessage();
        
		if (build.getResult() == Result.SUCCESS) {
			state = GHCommitState.SUCCESS;
		} else {
			state = GHCommitState.FAILURE;
            message = getFailMessage();
		}

		String publishedURL = GhprbTrigger.DESCRIPTOR.getPublishedURL();
		if (publishedURL != null && !publishedURL.isEmpty()) {
			message = message + " " + publishedURL + build.getUrl();
        }
        
        repo.createCommitStatus(build, state, message, pull);
	}
    
    private String getSuccessMessage() {
        return ":white_check_mark: Build passed";
    }
    
    private String getFailMessage() {
        return ":x: Build failed";
    }
}
