# knote

Aplicacion Spring Boot de notas basada en la guia de Learnk8s.

## Que incluye

- Spring Boot + FreeMarker para la interfaz.
- MongoDB para guardar las notas.
- Markdown renderizado a HTML con CommonMark.
- MinIO para guardar imagenes fuera del contenedor de la app.
- Dockerfile, Docker Compose y manifiestos Kubernetes.

## Ejecutar local con Docker Compose

Necesitas Docker Desktop abierto.

```powershell
docker compose up --build
```

Abre la app en:

```text
http://localhost:8080
```

La consola de MinIO queda disponible en:

```text
http://localhost:9001
usuario: mykey
clave: mysecret
```

Para apagar y borrar datos locales:

```powershell
docker compose down -v
```

## Ejecutar la app desde Maven

Primero levanta MongoDB y MinIO:

```powershell
docker compose up mongo minio
```

En otra terminal:

```powershell
.\mvnw.cmd spring-boot:run
```

## Kubernetes

Construye la imagen:

```powershell
docker build -t knote:2.0.0 .
```

Aplica los manifiestos:

```powershell
kubectl apply -f kube
```

Si tu cluster local no entrega IP externa para `LoadBalancer`, usa port-forward:

```powershell
kubectl port-forward service/knote 8080:80
```

Luego abre `http://localhost:8080`.

Para probar escalado:

```powershell
kubectl scale --replicas=3 deployment/knote
```

Para limpiar:

```powershell
kubectl delete -f kube
```
