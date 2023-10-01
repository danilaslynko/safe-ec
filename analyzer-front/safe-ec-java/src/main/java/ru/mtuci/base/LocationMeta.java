package ru.mtuci.base;

import com.contrastsecurity.sarif.ArtifactLocation;
import com.contrastsecurity.sarif.Location;
import com.contrastsecurity.sarif.LogicalLocation;
import com.contrastsecurity.sarif.PhysicalLocation;
import com.contrastsecurity.sarif.Region;
import com.contrastsecurity.sarif.Result;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import ru.mtuci.utils.VirtualPaths;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Objects;
import java.util.function.Consumer;

@Builder
@Getter
public class LocationMeta implements Consumer<Result>
{
    @NonNull
    private String path;
    private String className;
    private String funcName;
    private Integer linenumber;
    private String property;
    private String xpath;

    @Override
    public void accept(Result result)
    {
        var pathParts = new LinkedList<Path>();
        var temp = VirtualPaths.resolve(Paths.get(this.path));
        while (temp != null)
        {
            var fileName = temp.getFileName();
            if (fileName != null)
                pathParts.addFirst(fileName);
            temp = VirtualPaths.resolve(temp.getParent());
        }
        var path = pathParts.stream().filter(Objects::nonNull).reduce(Paths.get(this.path).getRoot(), Path::resolve);
        var artifact = new ArtifactLocation().withUri(path.toUri().toString());
        var physicalLocation = new PhysicalLocation().withArtifactLocation(artifact);
        var logicalLocations = new HashSet<LogicalLocation>();
        if (className != null)
        {
            String fullyQualifiedName = className;
            if ("<init>".equals(funcName))
                fullyQualifiedName += " constructor" + funcName.substring(6);
            else if ("<clinit>".equals(funcName))
                fullyQualifiedName += " static initializer";
            else if (funcName != null)
                fullyQualifiedName += "#" + funcName;

            logicalLocations.add(new LogicalLocation().withFullyQualifiedName(fullyQualifiedName));
        }
        if (property != null)
        {
            logicalLocations.add(new LogicalLocation().withFullyQualifiedName(property));
        }
        if (xpath != null)
        {
            logicalLocations.add(new LogicalLocation().withFullyQualifiedName(xpath));
        }

        if (linenumber != null)
        {
            physicalLocation.withRegion(new Region().withStartLine(linenumber));
        }

        var location = new Location().withPhysicalLocation(physicalLocation);
        result.withLocations(Collections.singletonList(location.withLogicalLocations(logicalLocations)));
    }
}
