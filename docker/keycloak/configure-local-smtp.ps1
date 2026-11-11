$ErrorActionPreference = 'Stop'

# This changes only the SMTP settings of the existing local realm.
$command = @'
/opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080 --realm master --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" >/dev/null && /opt/keycloak/bin/kcadm.sh update realms/nomion -s 'smtpServer={"host":"mailpit","port":"1025","from":"nomion@localhost","fromDisplayName":"Nomion","auth":"false","ssl":"false","starttls":"false"}'
'@
docker compose exec -T keycloak /bin/bash -c $command
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível configurar o SMTP local do Keycloak.' }
Write-Host 'SMTP local configurado. As mensagens serão capturadas pelo Mailpit.'
