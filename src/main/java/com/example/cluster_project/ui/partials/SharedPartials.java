package com.example.cluster_project.ui.partials;

import j2html.tags.DomContent;

import static j2html.TagCreator.*;
import static j2html.TagCreator.link;

public class SharedPartials {

  public static DomContent sharedHead(String title) {
    return head(
      title(title),
      script().withSrc("https://cdn.jsdelivr.net/npm/@sudodevnull/datastar").isDefer().withType("module"),
      link().withRel("icon").withType("image/x-icon").withHref("favicon.ico")
    );
  }
}
