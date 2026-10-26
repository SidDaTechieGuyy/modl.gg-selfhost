# Source provenance

This distribution derives from these AGPL-3.0-only projects:

| Source | Commit used |
| --- | --- |
| https://github.com/modl-gg/panel | `e7e747e32edcd3d81ae90fdb85da8ce5215dc2e7` |
| https://github.com/modl-gg/backend | `57d51ec8aa97cde8eab54e7fa258b04012d28cf1` |
| https://github.com/modl-gg/landing | `58dd19d3aa545afcd0f06dfdb28ea14121ef9784` |

The panel is retained in `frontend`, and the Java backend in `backend`. The setup
page and its form derive from the landing repository's `Registration.tsx` and
`RegistrationForm.tsx`; unrelated site content is omitted. Original license texts
are retained. Modifications remain subject to the AGPL, including its source
availability obligations when the software is used over a network.

Builds still use upstream shared UI, protocol, and replay packages from the modl
package registry. The Java replay-format archive supplied upstream is retained in
`backend/libs`. Keep the lockfile when building or updating the frontend.
