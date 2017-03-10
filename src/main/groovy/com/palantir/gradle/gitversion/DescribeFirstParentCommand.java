/*
 * Copyright 2017 Palantir Technologies
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * <http://www.apache.org/licenses/LICENSE-2.0>
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Derived from: https://github.com/eclipse/jgit/blob/3b4448637fbb9d74e0c9d44048ba76bb7c1214ce/org.eclipse.jgit/src/org/eclipse/jgit/api/DescribeCommand.java
 *
 * Copyright (C) 2013, CloudBees, Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.palantir.gradle.gitversion;

import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevFlagSet;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;

import static org.eclipse.jgit.lib.Constants.R_TAGS;

/**
 * Given a commit, show the most recent tag that is reachable from a commit. If a commit has multiple parent commits,
 * only consider the first one (match the behavior of "git describe --first-parent").
 *
 * Based on the org.eclipse.jgit.api.DescribeCommand. The only difference is that the FirstParentFilter is set as a
 * filter on the RevWalk used internally by the command.
 *
 * @since 3.2
 */
public class DescribeFirstParentCommand extends GitCommand<String> {
    private final RevWalk w;

    /**
     * Commit to describe.
     */
    private RevCommit target;

    /**
     * How many tags we'll consider as candidates.
     * This can only go up to the number of flags JGit can support in a walk,
     * which is 24.
     */
    private int maxCandidates = 10;

    /**
     * Whether to always use long output format or not.
     */
    private boolean longDesc;

    /**
     *
     * @param repo
     */
    protected DescribeFirstParentCommand(Repository repo) {
        super(repo);
        w = new RevWalk(repo);
        w.setRevFilter(new FirstParentFilter());
        w.setRetainBody(false);
    }

    /**
     * Sets the commit to be described.
     *
     * @param target
     * 		A non-null object ID to be described.
     * @return {@code this}
     * @throws MissingObjectException
     *             the supplied commit does not exist.
     * @throws IncorrectObjectTypeException
     *             the supplied id is not a commit or an annotated tag.
     * @throws IOException
     *             a pack file or loose object could not be read.
     */
    public DescribeFirstParentCommand setTarget(ObjectId target) throws IOException {
        this.target = w.parseCommit(target);
        return this;
    }

    /**
     * Sets the commit to be described.
     *
     * @param rev
     * 		Commit ID, tag, branch, ref, etc.
     * 		See {@link Repository#resolve(String)} for allowed syntax.
     * @return {@code this}
     * @throws IncorrectObjectTypeException
     *             the supplied id is not a commit or an annotated tag.
     * @throws RefNotFoundException
     * 				the given rev didn't resolve to any object.
     * @throws IOException
     *             a pack file or loose object could not be read.
     */
    public DescribeFirstParentCommand setTarget(String rev) throws IOException,
            RefNotFoundException {
        ObjectId id = repo.resolve(rev);
        if (id == null)
            throw new RefNotFoundException(MessageFormat.format(JGitText.get().refNotResolved, rev));
        return setTarget(id);
    }

    /**
     * Determine whether always to use the long format or not. When set to
     * <code>true</code> the long format is used even the commit matches a tag.
     *
     * @param longDesc
     *            <code>true</code> if always the long format should be used.
     * @return {@code this}
     *
     * @see <a
     *      href="https://www.kernel.org/pub/software/scm/git/docs/git-describe.html"
     *      >Git documentation about describe</a>
     * @since 4.0
     */
    public DescribeFirstParentCommand setLong(boolean longDesc) {
        this.longDesc = longDesc;
        return this;
    }

    private String longDescription(Ref tag, int depth, ObjectId tip)
            throws IOException {
        return String.format(
                "%s-%d-g%s", tag.getName().substring(R_TAGS.length()), //$NON-NLS-1$
                Integer.valueOf(depth), w.getObjectReader().abbreviate(tip)
                        .name());
    }

    /**
     * Describes the specified commit. Target defaults to HEAD if no commit was
     * set explicitly.
     *
     * @return if there's a tag that points to the commit being described, this
     *         tag name is returned. Otherwise additional suffix is added to the
     *         nearest tag, just like git-describe(1).
     *         <p>
     *         If none of the ancestors of the commit being described has any
     *         tags at all, then this method returns null, indicating that
     *         there's no way to describe this tag.
     */
    @Override
    public String call() throws GitAPIException {
        try {
            checkCallable();

            if (target == null)
                setTarget(Constants.HEAD);

            Map<ObjectId, Ref> tags = new HashMap<ObjectId, Ref>();

            for (Ref r : repo.getRefDatabase().getRefs(R_TAGS).values()) {
                ObjectId key = repo.peel(r).getPeeledObjectId();
                if (key == null)
                    key = r.getObjectId();
                tags.put(key, r);
            }

            // combined flags of all the candidate instances
            final RevFlagSet allFlags = new RevFlagSet();

            /**
             * Tracks the depth of each tag as we find them.
             */
            class Candidate {
                final Ref tag;
                final RevFlag flag;

                /**
                 * This field counts number of commits that are reachable from
                 * the tip but not reachable from the tag.
                 */
                int depth;

                Candidate(RevCommit commit, Ref tag) {
                    this.tag = tag;
                    this.flag = w.newFlag(tag.getName());
                    // we'll mark all the nodes reachable from this tag accordingly
                    allFlags.add(flag);
                    w.carry(flag);
                    commit.add(flag);
                    // As of this writing, JGit carries a flag from a child to its parents
                    // right before RevWalk.next() returns, so all the flags that are added
                    // must be manually carried to its parents. If that gets fixed,
                    // this will be unnecessary.
                    commit.carry(flag);
                }

                /**
                 * Does this tag contain the given commit?
                 */
                boolean reaches(RevCommit c) {
                    return c.has(flag);
                }

                String describe(ObjectId tip) throws IOException {
                    return longDescription(tag, depth, tip);
                }

            }
            List<Candidate> candidates = new ArrayList<Candidate>();    // all the candidates we find

            // is the target already pointing to a tag? if so, we are done!
            Ref lucky = tags.get(target);
            if (lucky != null) {
                return longDesc ? longDescription(lucky, 0, target) : lucky
                        .getName().substring(R_TAGS.length());
            }

            w.markStart(target);

            int seen = 0;   // commit seen thus far
            RevCommit c;
            while ((c = w.next()) != null) {
                if (!c.hasAny(allFlags)) {
                    // if a tag already dominates this commit,
                    // then there's no point in picking a tag on this commit
                    // since the one that dominates it is always more preferable
                    Ref t = tags.get(c);
                    if (t != null) {
                        Candidate cd = new Candidate(c, t);
                        candidates.add(cd);
                        cd.depth = seen;
                    }
                }

                // if the newly discovered commit isn't reachable from a tag that we've seen
                // it counts toward the total depth.
                for (Candidate cd : candidates) {
                    if (!cd.reaches(c))
                        cd.depth++;
                }

                // if we have search going for enough tags, we will start
                // closing down. JGit can only give us a finite number of bits,
                // so we can't track all tags even if we wanted to.
                if (candidates.size() >= maxCandidates)
                    break;

                // TODO: if all the commits in the queue of RevWalk has allFlags
                // there's no point in continuing search as we'll not discover any more
                // tags. But RevWalk doesn't expose this.
                seen++;
            }

            // at this point we aren't adding any more tags to our search,
            // but we still need to count all the depths correctly.
            while ((c = w.next()) != null) {
                if (c.hasAll(allFlags)) {
                    // no point in visiting further from here, so cut the search here
                    for (RevCommit p : c.getParents())
                        p.add(RevFlag.SEEN);
                } else {
                    for (Candidate cd : candidates) {
                        if (!cd.reaches(c))
                            cd.depth++;
                    }
                }
            }

            // if all the nodes are dominated by all the tags, the walk stops
            if (candidates.isEmpty())
                return null;

            Candidate best = Collections.min(candidates, new Comparator<Candidate>() {
                public int compare(Candidate o1, Candidate o2) {
                    return o1.depth - o2.depth;
                }
            });

            return best.describe(target);
        } catch (IOException e) {
            throw new JGitInternalException(e.getMessage(), e);
        } finally {
            setCallable(false);
            w.close();
        }
    }

    private static final class FirstParentFilter extends RevFilter {
        private Set<RevCommit> ignoreCommits = new HashSet<>();

        @Override
        public boolean include(RevWalk revWalk, RevCommit commit) throws IOException {
            for (int i = 1; i < commit.getParentCount(); i++) {
                // if a commit has more than one parent, ignore all parents except the first
                ignoreCommits.add(commit.getParent(i));
            }
            boolean include = true;
            if (ignoreCommits.contains(commit)) {
                include = false;
                ignoreCommits.remove(commit);
            }
            return include;
        }

        @Override
        public RevFilter clone() {
            return new FirstParentFilter();
        }
    }
}
