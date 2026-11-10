# Provisionamento de usuários com Keycloak

## Identidade e permissões

O Keycloak autentica o usuário e gerencia credenciais, recuperação de senha e sessões. O Nomion mantém o UUID de `User`, os vínculos em `TenantMembership` e os papéis e permissões de plataforma e de escritório. A configuração inicial usa um realm `nomion` por ambiente, compartilhado pelos escritórios daquele ambiente.

A API valida o access token antes de procurar o usuário local pelo par `iss` e `sub`. Uma migration Flyway adicionará esse par ao usuário com unicidade no banco e uma restrição que exige os dois valores juntos. Durante a transição, ambos podem ficar vazios para contas legadas ainda sem vínculo. E-mail é dado de contato e pode mudar. A API não cria nem associa usuários automaticamente a partir do e-mail do token.

## Primeiro administrador

1. O operador cria a identidade do primeiro administrador no realm `nomion` e configura o acesso por um fluxo do próprio Keycloak. Essa identidade é distinta da conta que administra o Keycloak.
2. O operador habilita temporariamente o setup do Nomion com um token aleatório e configura o `sub` esperado do administrador inicial. O emissor vem da configuração do Resource Server.
3. O administrador autenticado chama `POST /setup/initialize` com o access token, o token de setup e os dados do primeiro escritório. A API exige que o `sub` do token coincida com o configurado. O payload deixa de receber senha.
4. A transação existente, protegida pelo bloqueio do registro de setup, cria o usuário local vinculado à identidade, o escritório, a associação com `TENANT_ADMIN` e a atribuição de `PLATFORM_ADMIN`. Depois do sucesso, o operador desabilita o setup e remove a configuração temporária.

Um token OIDC válido de outra conta, um token de setup incorreto ou uma segunda inicialização devem ser rejeitados. O bootstrap do zero se aplica ao ambiente local recriado. Uma base já inicializada segue o procedimento de migração abaixo.

## Convites e novos vínculos

- **Usuário novo.** Um administrador com `USER_CREATE` e `ROLE_ASSIGN` no escritório selecionado solicita o convite com nome, e-mail e papéis. O backend cria a identidade no Keycloak, registra o `iss` e o `sub` retornado ou confirmado para essa identidade e grava o usuário, o vínculo e os papéis em uma transação local. Após a gravação, solicita ao Keycloak um e-mail com link de validade limitada para verificar o endereço e definir a senha. O realm terá cadastro público desativado e precisará de SMTP para o envio. [Ações por e-mail do Keycloak](https://www.keycloak.org/docs-api/latest/javadocs/org/keycloak/admin/client/resource/UserResource.html)
- **Usuário já provisionado.** O administrador autorizado reutiliza o fluxo de criação de vínculo por UUID em `POST /tenants/{tenantId}/memberships`. A nova associação preserva a conta e os papéis dos demais escritórios. Um vínculo duplicado deve ser rejeitado.
- **Falhas e retentativas.** A criação remota e a transação local podem falhar separadamente. A implementação deve persistir um identificador da operação antes de chamar o Keycloak e reutilizá-lo nas retentativas. Só deve retomar uma identidade remota que possa atribuir à própria operação. Conflitos com uma identidade preexistente exigem revisão. Se apenas o e-mail falhar, a retentativa reenvia o convite para a identidade já associada. Uma conta remota sem vínculo local não tem acesso à API.

O cliente administrativo é exclusivo do backend. Suas credenciais ficam fora do repositório e suas permissões se limitam às operações necessárias de provisionamento no realm. O cliente Angular usa o fluxo de login do usuário. A API determina os papéis a partir dos vínculos ativos no Nomion.

## Contas existentes e corte

O ambiente local parte de um banco novo. A existência de bases externas a preservar ainda precisa ser confirmada antes de um corte fora do desenvolvimento.

Se houver contas reais, faça um inventário dos usuários locais, preserve seus UUIDs e vínculos e associe cada um a uma identidade Keycloak por um procedimento administrativo verificável. Para contas sem identidade, crie a conta remota e convide o usuário a definir uma senha. Divergências de e-mail ou identidades remotas já existentes exigem revisão explícita. Os hashes BCrypt do Nomion ficam fora desse fluxo.

Valide primeiro o acesso dos administradores e os vínculos dos usuários. No corte, desative a aceitação dos tokens locais e invalide as sessões de refresh legadas. A retirada das colunas e tabelas de autenticação antigas fica para uma migration posterior, após a validação do corte.

## Verificações para a implementação

- Bootstrap funciona uma vez e recusa uma identidade diferente da configurada, mesmo com o token de setup correto.
- Alterar o e-mail no Keycloak não muda a identidade vinculada. Um token de outra identidade com o mesmo e-mail não herda acesso.
- Uma falha entre a criação remota, a gravação local e o envio do e-mail pode ser retomada sem criar contas ou vínculos duplicados.
- A mesma conta mantém papéis independentes em dois escritórios, e suspender um vínculo remove o acesso naquele escritório.
