# To add footer citation

Edit `PaperMod/layouts/_default/single.html`

```html
<div class="post-content">
    {{- if not (.Param "disableAnchoredHeadings") }}
    {{- partial "anchored_headings.html" .Content -}}
    {{- else }}{{ .Content }}{{ end }}
    {{- if not (.Param "disableCitation") }}
    {{- partial "citation.html" . }}
    {{- end }}
</div>
```

To enable the citation footer, add the following to the front matter of the post:

```md
useCitationFooter: true
```
