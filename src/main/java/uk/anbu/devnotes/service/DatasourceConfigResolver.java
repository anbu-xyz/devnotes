package uk.anbu.devnotes.service;

@FunctionalInterface
public interface DatasourceConfigResolver {
    ConfigService.DataSourceConfig resolve(String datasourceName);
}
