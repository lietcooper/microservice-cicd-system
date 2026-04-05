{{/*
Expand the name of the chart.
*/}}
{{- define "cicd.name" -}}
{{- .Chart.Name | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "cicd.fullname" -}}
{{- printf "%s-%s" .Release.Name .Chart.Name | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "cicd.labels" -}}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version | replace "+" "_" }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Compute the PostgreSQL JDBC URL:
  - internal: jdbc:postgresql://<release>-postgresql:5432/<db>
  - external: value of postgresql.externalUrl
*/}}
{{- define "cicd.postgresql.url" -}}
{{- if .Values.postgresql.enabled -}}
jdbc:postgresql://{{ .Release.Name }}-postgresql:5432/{{ .Values.postgresql.database }}
{{- else -}}
{{ .Values.postgresql.externalUrl }}
{{- end -}}
{{- end }}

{{- define "cicd.postgresql.username" -}}
{{- if .Values.postgresql.enabled -}}
{{ .Values.postgresql.username }}
{{- else -}}
{{ .Values.postgresql.externalUsername }}
{{- end -}}
{{- end }}

{{- define "cicd.postgresql.password" -}}
{{- if .Values.postgresql.enabled -}}
{{ .Values.postgresql.password }}
{{- else -}}
{{ .Values.postgresql.externalPassword }}
{{- end -}}
{{- end }}

{{/*
Compute the RabbitMQ host / port / credentials
*/}}
{{- define "cicd.rabbitmq.host" -}}
{{- if .Values.rabbitmq.enabled -}}
{{ .Release.Name }}-rabbitmq
{{- else -}}
{{ .Values.rabbitmq.externalHost }}
{{- end -}}
{{- end }}

{{- define "cicd.rabbitmq.port" -}}
{{- if .Values.rabbitmq.enabled -}}
5672
{{- else -}}
{{ .Values.rabbitmq.externalPort }}
{{- end -}}
{{- end }}

{{- define "cicd.rabbitmq.username" -}}
{{- if .Values.rabbitmq.enabled -}}
{{ .Values.rabbitmq.username }}
{{- else -}}
{{ .Values.rabbitmq.externalUsername }}
{{- end -}}
{{- end }}

{{/*
Compute MinIO endpoint / credentials
*/}}
{{- define "cicd.minio.endpoint" -}}
{{- if .Values.minio.enabled -}}
http://{{ .Release.Name }}-minio:{{ .Values.minio.port }}
{{- else -}}
{{ .Values.minio.externalEndpoint }}
{{- end -}}
{{- end }}

{{- define "cicd.minio.accessKey" -}}
{{- if .Values.minio.enabled -}}
{{ .Values.minio.accessKey }}
{{- else -}}
{{ .Values.minio.externalAccessKey }}
{{- end -}}
{{- end }}

{{- define "cicd.minio.secretKey" -}}
{{- if .Values.minio.enabled -}}
{{ .Values.minio.secretKey }}
{{- else -}}
{{ .Values.minio.externalSecretKey }}
{{- end -}}
{{- end }}

{{- define "cicd.rabbitmq.password" -}}
{{- if .Values.rabbitmq.enabled -}}
{{ .Values.rabbitmq.password }}
{{- else -}}
{{ .Values.rabbitmq.externalPassword }}
{{- end -}}
{{- end }}
