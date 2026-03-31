@import java.util.Map
@param Map<String, Double> rates
@param String errorMessage

<!DOCTYPE html>
<html lang="en">
<head>
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.3/css/all.min.css">
    <link rel="stylesheet" type="text/css" href="/css/style.css">
    <title>Exchange Rate Manager</title>
</head>
<body class="p-8">

<h1><i class="fas fa-dollar-sign"></i> Exchange Rate Manager</h1>

@if(errorMessage != null)
    <div class="encryption-error">
        <i class="fas fa-exclamation-triangle"></i> ${errorMessage}
    </div>
@endif

@if(rates.isEmpty())
    <p class="muted">No rates configured yet.</p>
@else
    <table class="db-meta-table">
        <thead>
        <tr>
            <th>Pair</th>
            <th>Rate</th>
            <th>Action</th>
        </tr>
        </thead>
        <tbody>
        @for(var entry : rates.entrySet())
            <tr>
                <td>${entry.getKey()}</td>
                <td>${String.format("%.6f", entry.getValue())}</td>
                <td>
                    <form action="/tools/exchange-rates/delete" method="post" style="margin:0">
                        <input type="hidden" name="pair" value="${entry.getKey()}">
                        <button type="submit" class="btn-danger" title="Delete">
                            <i class="fas fa-trash-alt"></i>
                        </button>
                    </form>
                </td>
            </tr>
        @endfor
        </tbody>
    </table>
@endif

<hr>

<h2>Add / Update Rate</h2>
<form action="/tools/exchange-rates" method="post" class="config-form">
    <div class="form-group">
        <label for="pair">Currency Pair:</label>
        <input type="text" id="pair" name="pair" class="config-input"
               placeholder="GBP/USD" pattern="[A-Za-z]{3}/[A-Za-z]{3}"
               required>
    </div>
    <div class="form-group">
        <label for="rate">Rate:</label>
        <input type="number" id="rate" name="rate" class="config-input"
               placeholder="1.2700" step="any" min="0.000001"
               required>
    </div>
    <div class="form-actions">
        <button type="submit" class="btn-blue-glow">
            <i class="fas fa-plus"></i>Add Rate
        </button>
    </div>
</form>

<hr>
<a href="/tools" class="btn-blue-glow"><i class="fas fa-arrow-left"></i>Back to Tools</a>

</body>
</html>

