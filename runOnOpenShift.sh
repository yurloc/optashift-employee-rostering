#!/usr/bin/env bash
set -e

readonly dir_backend=optaweb-employee-rostering-backend
readonly dir_frontend=optaweb-employee-rostering-frontend

# Change dir to the project root (where the script is located) to correctly resolve module paths.
# This is needed in case the script was called from a different location than the project root.
cd "$(dirname "$(readlink -f "$0")")"

# Fail fast if the project hasn't been built
if ! stat -t ${dir_backend}/target/*.jar > /dev/null 2>&1
then
  echo >&2 "ERROR: Backend not built! Build the project before running this script."
  exit 1
fi
if [[ ! -d ${dir_frontend}/docker/build ]]
then
  echo >&2 "ERROR: Frontend not built! Build the project before running this script."
  exit 1
fi

command -v oc > /dev/null 2>&1 || {
  echo >&2 "ERROR: The oc client tool needs to be installed to connect to OpenShift."
  exit 1
}

[[ -x $(command -v oc) ]] || {
  echo >&2 "ERROR: The oc client tool is not executable. Please make it executable by running \
‘chmod u+x \$(command -v oc)’."
  exit 1
}

# Print info about the current user and project
echo "Current user: $(oc whoami)"
# Check that the current user has at least one project
[[ -z "$(oc projects -q)" ]] && {
  echo >&2 "You have no projects. Use ‘oc new-project <project-name>’ to create one."
  exit 1
}
# Display info about the current project
oc project

# Check that the current project is empty
get_all=$(oc get all -o name)
if [[ -z "$get_all" ]]
then
  echo "The project appears to be empty."
else
  echo >&2
  echo >&2 "Project content:"
  echo >&2
  echo >&2 "$get_all"
  echo >&2
  echo >&2 "ERROR: The project is not empty."
  exit 1
fi

declare -l answer_continue # -l converts the value to lower case before it's assigned
read -r -p "Do you want to continue? [y/N]: " "answer_continue"
[[ "$answer_continue" == "y" ]] || {
  echo "Aborted."
  exit 0
}

# Set up PostgreSQL
oc new-app --name postgresql postgresql-persistent

# Backend
# -- binary build (upload local artifacts + Dockerfile)
oc new-build --name backend --strategy=docker --binary
oc start-build backend --from-dir=${dir_backend} --follow
# -- new app
oc new-app backend
# -- use PostgreSQL secret
oc set env dc/backend --from=secret/postgresql

# Frontend
# -- binary build
oc new-build --name frontend --strategy=docker --binary
oc start-build frontend --from-dir=${dir_frontend}/docker --follow
# -- new app
oc new-app frontend
# -- expose the service
oc expose svc/frontend
# -- change target port to 8080
oc patch route frontend -p '{"spec":{"port":{"targetPort":"8080-tcp"}}}'

echo
echo "You can access the application at http://$(oc get route frontend -o custom-columns=:spec.host | tr -d '\n') \
once the deployment is done."
