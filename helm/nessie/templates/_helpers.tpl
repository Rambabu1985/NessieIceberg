{{/**

  Copyright (C) 2024 Dremio

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.

**/}}

{{/*
Expand the name of the chart.
*/}}
{{- define "nessie.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "nessie.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "nessie.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "nessie.labels" -}}
helm.sh/chart: {{ include "nessie.chart" . }}
{{ include "nessie.selectorLabels" . }}
app.kubernetes.io/version: {{ .Chart.Version | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "nessie.selectorLabels" -}}
app.kubernetes.io/name: {{ include "nessie.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "nessie.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "nessie.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Convert a dict into a string formed by a comma-separated list of key-value pairs: key1=value1,key2=value2, ...
*/}}
{{- define "nessie.dictToString" -}}
{{- $list := list -}}
{{- range $k, $v := . -}}
{{- $list = append $list (printf "%s=%s" $k $v) -}}
{{- end -}}
{{ join "," $list }}
{{- end -}}

{{- define "nessie.mergeAdvancedConfig" -}}
{{- $advConfig := index . 0 -}}
{{- $prefix := index . 1 -}}
{{- $dest := index . 2 -}}
{{- range $key, $val := $advConfig -}}
{{- $name := ternary $key (print $prefix "." $key) (eq $prefix "") -}}
{{- if kindOf $val | eq "map" -}}
{{- list $val $name $dest | include "nessie.mergeAdvancedConfig" -}}
{{- else -}}
{{- $_ := set $dest $name $val -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{/*
Determine the datasource kind based on the jdbcUrl. This relies on the fact that datasource
names should coincide with jdbc schemes in connection URIs.
*/}}
{{- define "nessie.dbKind" -}}
{{- $v := . | split ":" -}}
{{ $v._1 }}
{{- end }}

{{/*
Apply Nessie Catalog (Iceberg REST) options.
*/}}
{{- define "nessie.applyCatalogIcebergOptions" -}}
{{- $root := index . 0 -}}{{/* the object to introspect */}}
{{- $map := index . 1 -}}{{/* the destination map */}}
{{- with $root -}}
{{- $_ := set $map "nessie.catalog.default-warehouse" .defaultWarehouse -}}
{{- $_ = set $map "nessie.catalog.object-stores.health-check.enabled" .objectStoresHealthCheckEnabled -}}
{{- range $k, $v := .configDefaults -}}
{{- $_ = set $map ( printf "nessie.catalog.iceberg-config-defaults.%s" $k ) $v -}}
{{- end -}}
{{- range $k, $v := .configOverrides -}}
{{- $_ = set $map ( printf "nessie.catalog.iceberg-config-overrides.%s" $k ) $v -}}
{{- end -}}
{{- range $i, $warehouse := .warehouses -}}
{{- if not $warehouse.name -}}{{- required ( printf "catalog.iceberg.warehouses[%d]: missing warehouse name" $i ) $warehouse.name -}}{{- end -}}
{{- $_ = set $map ( printf "nessie.catalog.warehouses.%s.location" ( quote $warehouse.name ) ) $warehouse.location -}}
{{- range $k, $v := $warehouse.configDefaults -}}
{{- $_ = set $map ( printf "nessie.catalog.warehouses.%s.iceberg-config-defaults.%s" ( quote $warehouse.name ) $k ) $v -}}
{{- end -}}
{{- range $k, $v := $warehouse.configOverrides -}}
{{- $_ = set $map ( printf "nessie.catalog.warehouses.%s.iceberg-config-overrides.%s" ( quote $warehouse.name ) $k ) $v -}}
{{- end -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{/*
Apply S3 catalog options.
*/}}
{{- define "nessie.applyCatalogStorageS3RootOptions" -}}
{{- $root := index . 0 -}}{{/* the object to introspect */}}
{{- $prefix := index . 1 -}}{{/* the current prefix */}}
{{- $map := index . 2 -}}{{/* the destination map */}}
{{- with $root -}}
{{- if .transport -}}
{{- if .transport.maxHttpConnections -}}{{- $_ := set $map ( print $prefix "http.max-http-connections" ) .transport.maxHttpConnections -}}{{- end -}}
{{- if .transport.readTimeout -}}{{- $_ := set $map ( print $prefix "http.read-timeout" ) .transport.readTimeout -}}{{- end -}}
{{- if .transport.connectTimeout -}}{{- $_ := set $map ( print $prefix "http.connect-timeout" ) .transport.connectTimeout -}}{{- end -}}
{{- if .transport.connectionAcquisitionTimeout -}}{{- $_ := set $map ( print $prefix "http.connection-acquisition-timeout" ) .transport.connectionAcquisitionTimeout -}}{{- end -}}
{{- if .transport.connectionMaxIdleTime -}}{{- $_ := set $map ( print $prefix "http.connection-max-idle-time" ) .transport.connectionMaxIdleTime -}}{{- end -}}
{{- if .transport.connectionTimeToLive -}}{{- $_ := set $map ( print $prefix "http.connection-time-to-live" ) .transport.connectionTimeToLive -}}{{- end -}}
{{- if .transport.expectContinueEnabled -}}{{- $_ := set $map ( print $prefix "http.expect-continue-enabled" ) .transport.expectContinueEnabled -}}{{- end -}}
{{- end -}}
{{- if .sessionCredentials }}
{{- if .sessionCredentials.sessionCredentialRefreshGracePeriod -}}{{- $_ := set $map ( print $prefix "sts.session-grace-period" ) .sessionCredentials.sessionCredentialRefreshGracePeriod -}}{{- end -}}
{{- if .sessionCredentials.sessionCredentialCacheMaxEntries -}}{{- $_ := set $map ( print $prefix "sts.session-cache-max-size" ) .sessionCredentials.sessionCredentialCacheMaxEntries -}}{{- end -}}
{{- if .sessionCredentials.stsClientsCacheMaxEntries -}}{{- $_ := set $map ( print $prefix "sts.clients-cache-max-size" ) .sessionCredentials.stsClientsCacheMaxEntries -}}{{- end -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{- define "nessie.applyCatalogStorageS3BucketOptions" -}}
{{- $root := index . 0 -}}{{/* the object to introspect */}}
{{- $prefix := index . 1 -}}{{/* the current prefix */}}
{{- $map := index . 2 -}}{{/* the destination map */}}
{{- with $root -}}
{{- if .name -}}{{- $_ := set $map ( print $prefix "name" ) .name -}}{{- end -}}
{{- if .region -}}{{- $_ := set $map ( print $prefix "region" ) .region -}}{{- end -}}
{{- if .endpoint -}}{{- $_ := set $map ( print $prefix "endpoint" ) .endpoint -}}{{- end -}}
{{- if .externalEndpoint -}}{{- $_ := set $map ( print $prefix "external-endpoint" ) .externalEndpoint -}}{{- end -}}
{{- if .pathStyleAccess -}}{{- $_ := set $map ( print $prefix "path-style-access" ) .pathStyleAccess -}}{{- end -}}
{{- if .accessPoint -}}{{- $_ := set $map ( print $prefix "access-point" ) .accessPoint -}}{{- end -}}
{{- if .allowCrossRegionAccessPoint -}}{{- $_ := set $map ( print $prefix "allow-cross-region-access-point" ) .allowCrossRegionAccessPoint -}}{{- end -}}
{{- if .requestSigningEnabled -}}{{- $_ := set $map ( print $prefix "request-signing-enabled" ) .requestSigningEnabled -}}{{- end -}}
{{- if .authType -}}{{- $_ := set $map ( print $prefix "auth-type" ) .authType -}}{{- end -}}
{{- if .stsEndpoint -}}{{- $_ := set $map ( print $prefix "sts-endpoint" ) .assumeRole.stsEndpoint -}}{{- end -}}
{{- if .clientIam -}}
{{- if .clientIam.enabled -}}{{- $_ := set $map ( print $prefix "client-iam.enabled" ) .clientIam.enabled -}}{{- end -}}
{{- if .clientIam.policy -}}{{- $_ := set $map ( print $prefix "client-iam.policy" ) .clientIam.policy -}}{{- end -}}
{{- if .clientIam.roleArn -}}{{- $_ := set $map ( print $prefix "client-iam.assume-role" ) .clientIam.roleArn -}}{{- end -}}
{{- if .clientIam.roleSessionName -}}{{- $_ := set $map ( print $prefix "client-iam.role-session-name" ) .clientIam.roleSessionName -}}{{- end -}}
{{- if .clientIam.externalId -}}{{- $_ := set $map ( print $prefix "client-iam.external-id" ) .clientIam.externalId -}}{{- end -}}
{{- if .clientIam.sessionDuration -}}{{- $_ := set $map ( print $prefix "client-iam.session-duration" ) .clientIam.sessionDuration -}}{{- end -}}
{{- if .clientIam.statements -}}
{{- range $i, $statement := .clientIam.statements -}}
{{- $_ := set $map ( print $prefix "client-iam.statements[%d]" $i ) $statement -}}
{{- end -}}
{{- end -}}
{{- end -}}
{{- if .serverIam -}}
{{- if .serverIam.enabled -}}{{- $_ := set $map ( print $prefix "server-iam.enabled" ) .serverIam.enabled -}}{{- end -}}
{{- if .serverIam.policy -}}{{- $_ := set $map ( print $prefix "server-iam.policy" ) .serverIam.policy -}}{{- end -}}
{{- if .serverIam.roleArn -}}{{- $_ := set $map ( print $prefix "server-iam.ssume-role" ) .serverIam.roleArn -}}{{- end -}}
{{- if .serverIam.roleSessionName -}}{{- $_ := set $map ( print $prefix "server-iam.role-session-name" ) .serverIam.roleSessionName -}}{{- end -}}
{{- if .serverIam.externalId -}}{{- $_ := set $map ( print $prefix "server-iam.external-id" ) .serverIam.externalId -}}{{- end -}}
{{- if .serverIam.sessionDuration -}}{{- $_ := set $map ( print $prefix "server-iam.session-duration" ) .serverIam.sessionDuration -}}{{- end -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{/*
Apply GCS catalog options.
*/}}
{{- define "nessie.applyCatalogStorageGcsRootOptions" -}}
{{- $root := index . 0 -}}{{/* the object to introspect */}}
{{- $prefix := index . 1 -}}{{/* the current prefix */}}
{{- $map := index . 2 -}}{{/* the destination map */}}
{{- with $root -}}
{{- if .transport -}}
{{- if .transport.maxAttempts -}}{{- $_ := set $map ( print $prefix "max-attempts" ) .transport.maxAttempts -}}{{- end -}}
{{- if .transport.connectTimeout -}}{{- $_ := set $map ( print $prefix "connect-timeout" ) .transport.connectTimeout -}}{{- end -}}
{{- if .transport.readTimeout -}}{{- $_ := set $map ( print $prefix "read-timeout" ) .transport.readTimeout -}}{{- end -}}
{{- if .transport.initialRetryDelay -}}{{- $_ := set $map ( print $prefix "initial-retry-delay" ) .transport.initialRetryDelay -}}{{- end -}}
{{- if .transport.maxRetryDelay -}}{{- $_ := set $map ( print $prefix "max-retry-delay" ) .transport.maxRetryDelay -}}{{- end -}}
{{- if .transport.retryDelayMultiplier -}}{{- $_ := set $map ( print $prefix "retry-delay-multiplier" ) .transport.retryDelayMultiplier -}}{{- end -}}
{{- if .transport.initialRpcTimeout -}}{{- $_ := set $map ( print $prefix "initial-rpc-timeout" ) .transport.initialRpcTimeout -}}{{- end -}}
{{- if .transport.maxRpcTimeout -}}{{- $_ := set $map ( print $prefix "max-rpc-timeout" ) .transport.maxRpcTimeout -}}{{- end -}}
{{- if .transport.rpcTimeoutMultiplier -}}{{- $_ := set $map ( print $prefix "rpc-timeout-multiplier" ) .transport.rpcTimeoutMultiplier -}}{{- end -}}
{{- if .transport.logicalTimeout -}}{{- $_ := set $map ( print $prefix "logical-timeout" ) .transport.logicalTimeout -}}{{- end -}}
{{- if .transport.totalTimeout -}}{{- $_ := set $map ( print $prefix "total-timeout" ) .transport.totalTimeout -}}{{- end -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{- define "nessie.applyCatalogStorageGcsBucketOptions" -}}
{{- $root := index . 0 -}}{{/* the object to introspect */}}
{{- $prefix := index . 1 -}}{{/* the current prefix */}}
{{- $map := index . 2 -}}{{/* the destination map */}}
{{- with $root -}}
{{- if .name -}}{{- $_ := set $map ( print $prefix "name" ) .name -}}{{- end -}}
{{- if .host -}}{{- $_ := set $map ( print $prefix "host" ) .host -}}{{- end -}}
{{- if .externalHost -}}{{- $_ := set $map ( print $prefix "external-host" ) .externalHost -}}{{- end -}}
{{- if .userProject -}}{{- $_ := set $map ( print $prefix "user-project" ) .userProject -}}{{- end -}}
{{- if .projectId -}}{{- $_ := set $map ( print $prefix "project-id" ) .projectId -}}{{- end -}}
{{- if .quotaProjectId -}}{{- $_ := set $map ( print $prefix "quota-project-id" ) .quotaProjectId -}}{{- end -}}
{{- if .clientLibToken -}}{{- $_ := set $map ( print $prefix "client-lib-token" ) .clientLibToken -}}{{- end -}}
{{- if .authType -}}{{- $_ := set $map ( print $prefix "auth-type" ) .authType -}}{{- end -}}
{{- if .encryptionKey -}}{{- $_ := set $map ( print $prefix "encryption-key" ) .encryptionKey -}}{{- end -}}
{{- if .decryptionKey -}}{{- $_ := set $map ( print $prefix "decryption-key" ) .decryptionKey -}}{{- end -}}
{{- if .readChunkSize -}}{{- $_ := set $map ( print $prefix "read-chunk-size" ) .readChunkSize -}}{{- end -}}
{{- if .writeChunkSize -}}{{- $_ := set $map ( print $prefix "write-chunk-size" ) .writeChunkSize -}}{{- end -}}
{{- if .deleteBatchSize -}}{{- $_ := set $map ( print $prefix "delete-batch-size" ) .deleteBatchSize -}}{{- end -}}
{{- end -}}
{{- end -}}

{{/*
Apply ADLS catalog options.
*/}}
{{- define "nessie.applyCatalogStorageAdlsRootOptions" -}}
{{- $root := index . 0 -}}{{/* the object to introspect */}}
{{- $prefix := index . 1 -}}{{/* the current prefix */}}
{{- $map := index . 2 -}}{{/* the destination map */}}
{{- with $root -}}
{{- if .transport -}}
{{- if .transport.maxHttpConnections -}}{{- $_ := set $map ( print $prefix "max-http-connections" ) .transport.maxHttpConnections -}}{{- end -}}
{{- if .transport.connectTimeout -}}{{- $_ := set $map ( print $prefix "connect-timeout" ) .transport.connectTimeout -}}{{- end -}}
{{- if .transport.readTimeout -}}{{- $_ := set $map ( print $prefix "read-timeout" ) .transport.readTimeout -}}{{- end -}}
{{- if .transport.writeTimeout -}}{{- $_ := set $map ( print $prefix "write-timeout" ) .transport.writeTimeout -}}{{- end -}}
{{- if .transport.connectionIdleTimeout -}}{{- $_ := set $map ( print $prefix "connection-idle-timeout" ) .transport.connectionIdleTimeout -}}{{- end -}}
{{- if .transport.readBlockSize -}}{{- $_ := set $map ( print $prefix "read-block-size" ) .transport.readBlockSize -}}{{- end -}}
{{- if .transport.writeBlockSize -}}{{- $_ := set $map ( print $prefix "write-block-size" ) .transport.writeBlockSize -}}{{- end -}}
{{- end -}}
{{- list .advancedConfig ( print $prefix "configuration" ) $map | include "nessie.mergeAdvancedConfig" }}
{{- end -}}
{{- end -}}

{{- define "nessie.applyCatalogStorageAdlsFileSystemOptions" -}}
{{- $root := index . 0 -}}{{/* the object to introspect */}}
{{- $prefix := index . 1 -}}{{/* the current prefix */}}
{{- $map := index . 2 -}}{{/* the destination map */}}
{{- with $root -}}
{{- if .name -}}{{- $_ := set $map ( print $prefix "name" ) .name -}}{{- end -}}
{{- if .endpoint -}}{{- $_ := set $map ( print $prefix "endpoint" ) .endpoint -}}{{- end -}}
{{- if .externalEndpoint -}}{{- $_ := set $map ( print $prefix "external-endpoint" ) .externalEndpoint -}}{{- end -}}
{{- if .retryPolicy -}}{{- $_ := set $map ( print $prefix "retry-policy" ) .retryPolicy -}}{{- end -}}
{{- if .maxRetries -}}{{- $_ := set $map ( print $prefix "max-retries" ) .maxRetries -}}{{- end -}}
{{- if .tryTimeout -}}{{- $_ := set $map ( print $prefix "try-timeout" ) .tryTimeout -}}{{- end -}}
{{- if .retryDelay -}}{{- $_ := set $map ( print $prefix "retry-delay" ) .retryDelay -}}{{- end -}}
{{- if .maxRetryDelay -}}{{- $_ := set $map ( print $prefix "max-retry-delay" ) .maxRetryDelay -}}{{- end -}}
{{- if .authType -}}{{- $_ := set $map ( print $prefix "auth-type" ) .authType -}}{{- end -}}
{{- end -}}
{{- end -}}

{{/*
Define environkent variables for catalog storage options.
*/}}
{{- define "nessie.catalogStorageEnv" -}}
{{ $global := .}}
{{- include "nessie.catalogSecretToEnv" (list .Values.catalog.storage.s3.defaultOptions.accessKeySecret "awsAccessKeyId" "s3.default-options.access-key" "name" true . ) }}
{{- include "nessie.catalogSecretToEnv" (list .Values.catalog.storage.s3.defaultOptions.accessKeySecret "awsSecretAccessKey" "s3.default-options.access-key" "secret" false . ) }}
{{- range $i, $bucket := .Values.catalog.storage.s3.buckets -}}
{{- with $global }}
{{- include "nessie.catalogSecretToEnv" (list $bucket.accessKeySecret "awsAccessKeyId" (printf "s3.buckets.bucket%d.access-key" (add $i 1)) "name" true . ) }}
{{- include "nessie.catalogSecretToEnv" (list $bucket.accessKeySecret "awsSecretAccessKey" (printf "s3.buckets.bucket%d.access-key" (add $i 1)) "secret" false . ) }}
{{- end -}}
{{- end -}}
{{- include "nessie.catalogSecretToEnv" (list .Values.catalog.storage.gcs.defaultOptions.authCredentialsJsonSecret "key" "gcs.default-options.auth-credentials-json" "key" true . ) }}
{{- include "nessie.catalogSecretToEnv" (list .Values.catalog.storage.gcs.defaultOptions.oauth2TokenSecret "token" "gcs.default-options.oauth-token" "token" true . ) }}
{{- include "nessie.catalogSecretToEnv" (list .Values.catalog.storage.gcs.defaultOptions.oauth2TokenSecret "expiresAt" "gcs.default-options.oauth-token" "expiresAt" false . ) }}
{{- range $i, $bucket := .Values.catalog.storage.gcs.buckets -}}
{{- with $global }}
{{- include "nessie.catalogSecretToEnv" (list $bucket.authCredentialsJsonSecret "key" (printf "gcs.buckets.bucket%d.auth-credentials-json" (add $i 1)) "key" true . ) }}
{{- include "nessie.catalogSecretToEnv" (list $bucket.oauth2TokenSecret "token" (printf "gcs.buckets.bucket%d.oauth-token" (add $i 1)) "token" true . ) }}
{{- include "nessie.catalogSecretToEnv" (list $bucket.oauth2TokenSecret "expiresAt" (printf "gcs.buckets.bucket%d.oauth-token" (add $i 1)) "expiresAt" false . ) }}
{{- end -}}
{{- end -}}
{{ include "nessie.catalogSecretToEnv" (list .Values.catalog.storage.adls.defaultOptions.accountSecret "accountName" "adls.default-options.account" "name" true . ) }}
{{- include "nessie.catalogSecretToEnv" (list .Values.catalog.storage.adls.defaultOptions.accountSecret "accountKey" "adls.default-options.account" "secret" false . ) }}
{{- include "nessie.catalogSecretToEnv" (list .Values.catalog.storage.adls.defaultOptions.sasTokenSecret "sasToken" "adls.default-options.sas-token" "token" true . ) }}
{{- range $i, $filesystem := .Values.catalog.storage.adls.filesystems -}}
{{- with $global }}
{{- include "nessie.catalogSecretToEnv" (list $filesystem.accountSecret "accountName" (printf "adls.file-systems.filesystem%d.account" (add $i 1)) "name" true . ) }}
{{- include "nessie.catalogSecretToEnv" (list $filesystem.accountSecret "accountKey" (printf "adls.file-systems.filesystem%d.account" (add $i 1)) "secret" false . ) }}
{{- include "nessie.catalogSecretToEnv" (list $filesystem.sasTokenSecret "sasToken" (printf "adls.file-systems.filesystem%d.sas-token" (add $i 1)) "token" true . ) }}
{{- end -}}
{{- end -}}
{{- end -}}

{{/*
Define an env var from secret key.

Secrets are (can be) composite values - think of a username+password.
Secrets are not (no longer) present (or directly resolvable) from the bucket option types, but have to be resolved
via a symbolic name, which is something like 'nessie-catalog-secrets.s3.default-options.access-key'. The bucket
config types know about that symbolic name and resolve it via a SecretsProvider, which resolves via Quarkus' config.

*/}}
{{- define "nessie.catalogSecretToEnv" -}}
{{- $secret := index . 0 -}}
{{- $key := index . 1 -}}
{{- $midfix := index . 2 -}}
{{- $suffix := index . 3 -}}
{{- $addRef := index . 4 -}}
{{- $global := index . 5 -}}
{{- if $secret -}}
{{- $secretName := get $secret "name" -}}
{{- $secretKey := get $secret $key -}}
{{- with $global -}}
{{- if (and $secretName $secretKey) -}}
{{ if $addRef -}}
#
# {{ $midfix }}
#
- name: {{ (printf "nessie.catalog.service.%s" $midfix) | quote }}
  value: {{ (printf "urn:nessie-secret:quarkus:nessie-catalog-secrets.%s" $midfix) | quote }}
{{- end }}
- name: {{ (printf "nessie-catalog-secrets.%s.%s" $midfix $suffix) | quote }}
  valueFrom:
    secretKeyRef:
      name: {{ (tpl $secretName . ) | quote }}
      key: {{ (tpl $secretKey . ) | quote }}
{{ end -}}
{{- end -}}
{{- end -}}
{{- end -}}


{{/*
Define an env var from secret key.
*/}}
{{- define "nessie.secretToEnv" -}}
{{- $secret := index . 0 -}}
{{- $key := index . 1 -}}
{{- $envVarName := index . 2 -}}
{{- $global := index . 3 -}}
{{- if $secret -}}
{{- $secretName := get $secret "name" -}}
{{- $secretKey := get $secret $key -}}
{{- with $global -}}
{{- if (and $secretName $secretKey) -}}
- name: {{ $envVarName | quote }}
  valueFrom:
    secretKeyRef:
      name: {{ (tpl $secretName . ) | quote }}
      key: {{ (tpl $secretKey . ) | quote }}
{{ end -}}
{{- end -}}
{{- end -}}
{{- end -}}
