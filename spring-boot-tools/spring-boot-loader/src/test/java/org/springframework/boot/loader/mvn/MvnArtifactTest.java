/*
 * Copyright 2012-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.boot.loader.mvn;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public class MvnArtifactTest {

	@Test
	public void parseRelease() throws Exception {
		MvnArtifact ma = MvnArtifact.parse("my.group:my.artifact:1.0");
		Assert.assertEquals("my.group", ma.getGroupId());
		Assert.assertEquals("my.artifact", ma.getArtifactId());
		Assert.assertEquals("1.0", ma.getVersion());
		Assert.assertEquals("jar", ma.getPackaging());
		Assert.assertEquals("my/group/my.artifact/1.0/my.artifact-1.0.jar", ma.getPath());
	}

	@Test
	public void parseClassifier() throws Exception {
		MvnArtifact ma = MvnArtifact.parse("my.group:my.artifact:1.0:jar:sources");
		Assert.assertEquals("1.0", ma.getVersion());
		Assert.assertEquals("jar", ma.getPackaging());
		Assert.assertEquals("sources", ma.getClassifier());
		Assert.assertEquals("my/group/my.artifact/1.0/my.artifact-1.0-sources.jar", ma.getPath());
	}

	@Test
	public void parseSnapshot() throws Exception {
		MvnArtifact ma = MvnArtifact.parse("mygroup:myartifact:1.0-SNAPSHOT");
		ma.setResolvedSnapshotVersion("20140131.123456");
		Assert.assertEquals("mygroup", ma.getGroupId());
		Assert.assertEquals("myartifact", ma.getArtifactId());
		Assert.assertEquals("1.0-SNAPSHOT", ma.getVersion());
		Assert.assertEquals("jar", ma.getPackaging());
	}

}
