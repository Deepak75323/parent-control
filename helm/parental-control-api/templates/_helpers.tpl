{{- define "parental-control-api.secretName" -}}
{{- if .Values.secrets.existingSecret -}}
{{ .Values.secrets.existingSecret }}
{{- else -}}
{{ .Chart.Name }}-secrets
{{- end -}}
{{- end -}}
