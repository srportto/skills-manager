# Manifests Kubernetes

Leia este arquivo quando for escrever ou revisar Deployment, Service, ConfigMap e Ingress da aplicação (variante `k8s`). Manifests prontos: [k8s-deployment.yaml](../assets/k8s-deployment.yaml) e [k8s-service.yaml](../assets/k8s-service.yaml). Probes e shutdown: [probes-graceful-shutdown](probes-graceful-shutdown.md).

## Deployment + Service (mínimo completo)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: minha-app
spec:
  replicas: 2
  selector:
    matchLabels:
      app: minha-app
  template:
    metadata:
      labels:
        app: minha-app
    spec:
      terminationGracePeriodSeconds: 30
      containers:
        - name: minha-app
          image: minha-app:latest
          ports:
            - containerPort: 8080
          envFrom:
            - configMapRef:
                name: minha-app-config
          startupProbe:                 # protege a subida lenta da JVM sem afrouxar a liveness
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            periodSeconds: 5
            failureThreshold: 24        # até 120 s para subir
          readinessProbe:               # readinessState + dependências necessárias (ex.: db)
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            periodSeconds: 10
            failureThreshold: 3
          livenessProbe:                # só o estado do processo; nunca banco/broker/cache
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            periodSeconds: 15
            failureThreshold: 3
          lifecycle:
            preStop:                    # dá tempo do endpoint sair do balanceador antes do SIGTERM
                                        # (ação sleep: Kubernetes 1.30+; antes, exec com "sleep 5")
              sleep:
                seconds: 5
          resources:
            requests:
              memory: "512Mi"
              cpu: "250m"
            limits:
              memory: "768Mi"
              cpu: "1000m"
          env:
            - name: JAVA_TOOL_OPTIONS
              value: "-XX:MaxRAMPercentage=75"
---
apiVersion: v1
kind: Service
metadata:
  name: minha-app
spec:
  selector:
    app: minha-app
  ports:
    - port: 80
      targetPort: 8080
```

## ConfigMap (env vars)

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: minha-app-config
data:
  SPRING_PROFILES_ACTIVE: "producao"
```

## Ingress (exposição externa, referencia o Service já criado acima)

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: minha-app-ingress
spec:
  ingressClassName: nginx
  rules:
    - host: myapp.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: minha-app
                port:
                  number: 80
```


## Exemplo antes/depois: probes e shutdown

```yaml
# ANTES — liveness depende de banco (queda do banco reinicia todos os pods), sem preStop, sem folga de shutdown
livenessProbe:
  httpGet: { path: /actuator/health/readiness, port: 8080 }
terminationGracePeriodSeconds: 30     # = timeout-per-shutdown-phase de 30s: SIGKILL antes de terminar
```

```yaml
# DEPOIS — liveness só do processo; readiness com dependências; orçamento 5 s + 25 s < 40 s
livenessProbe:
  httpGet: { path: /actuator/health/liveness, port: 8080 }
readinessProbe:
  httpGet: { path: /actuator/health/readiness, port: 8080 }
lifecycle:
  preStop: { sleep: { seconds: 5 } }
terminationGracePeriodSeconds: 40
```

Manifests completos e validados (YAML parseável): [k8s-deployment.yaml](../assets/k8s-deployment.yaml) e
[k8s-service.yaml](../assets/k8s-service.yaml). O Deployment pronto acrescenta `securityContext` não-root
(`runAsNonRoot`, sem escalonamento de privilégio), `requests`/`limits` e `JAVA_TOOL_OPTIONS`.
