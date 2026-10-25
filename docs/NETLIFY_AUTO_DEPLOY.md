# Netlify auto-deploy setup

The `Deploy Auriga website to Netlify` GitHub Actions workflow validates the website on every push to `auriga-arael-1` or `main`, then deploys `website/web_deploy` and `website/netlify/functions` to the configured Netlify site as a production deploy.

## One-time setup still required

The workflow intentionally fails instead of pretending a deploy succeeded when credentials are missing. Add these two repository Actions secrets:

1. In Netlify, open **User settings → Applications** and create a personal access token. Add it in GitHub as `NETLIFY_AUTH_TOKEN`.
2. In Netlify, open the target site's **Site configuration → General → Site details** and copy its API ID / Site ID. Add it in GitHub as `NETLIFY_SITE_ID`.
3. In GitHub, open **mikerr001/_AURIGA2 → Settings → Secrets and variables → Actions → New repository secret** and create both secrets. Never commit the token to a file or paste it into source code.
4. Open **Actions → Deploy Auriga website to Netlify** and rerun the latest failed deployment, or push a new commit.

After that, each push to `auriga-arael-1` or `main` runs source checks and deploys the current website to production. A push containing only Android code also triggers a website deploy by design.

## What the workflow checks

- Required website entry pages and Netlify function exist.
- The feedback function passes Node syntax validation.
- Local references in the shipped HTML resolve.
- The web app manifest is valid JSON.

A green validation step alone is not a deployment. The final Netlify CLI step must also succeed for the public site to have been updated.
