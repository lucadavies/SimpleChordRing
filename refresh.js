var myVar = setInterval(loadDoc, 1000);

function loadDoc()
{
    var xhttp = new XMLHttpRequest();
    xhttp.onreadystatechange = function()
    {
        if (this.readyState == 4 && this.status == 200)
        {
            document.getElementById("tabResults").innerHTML = this.responseText;
        }
    };
    xhttp.open("GET", "results/update", true);
    xhttp.send();
}