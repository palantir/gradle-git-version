package com.palantir.gradle.gitversion;

import org.junit.Test;

public class GitVersionArgsTest {
    @Test
    public void allowed_prefixes() throws Exception {
        new GitVersionArgs().setPrefix("@Product@");
        new GitVersionArgs().setPrefix("abc@");
        new GitVersionArgs().setPrefix("abc@test@");
        new GitVersionArgs().setPrefix("Abc-aBc-abC@");
        new GitVersionArgs().setPrefix("foo-bar@");
        new GitVersionArgs().setPrefix("foo-bar/");
        new GitVersionArgs().setPrefix("foo-bar-");
        new GitVersionArgs().setPrefix("foo/bar@");
        new GitVersionArgs().setPrefix("Foo/Bar@");
    }

    @Test(expected = IllegalStateException.class)
    public void require_dash_or_at_symbol_at_prefix_end() throws Exception {
        new GitVersionArgs().setPrefix("v");
    }
}
